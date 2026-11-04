package com.kdevs.livo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** User-Agent used for downloads, availability checks and playback, unless a channel sets its own. */
const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Livo) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

/** "https://host/path" -> "https://host", for the Origin header. */
fun originOf(url: String): String? = runCatching {
    val u = URI(url)
    "${u.scheme}://${u.authority}"
}.getOrNull()

class PlaylistRepository(context: Context) {
    private val dir = context.filesDir
    private val downloader = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val checker = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build()

    /**
     * iptv-org's per-language playlists (ISO 639-3 codes). Used to tag channels
     * when the main playlist has no tvg-language attribute.
     */
    private val languageFiles = mapOf(
        "English" to "eng",
        "Hindi" to "hin",
        "Tamil" to "tam",
        "Telugu" to "tel",
        "Malayalam" to "mal",
        "Kannada" to "kan",
        "Bengali" to "ben",
        "Marathi" to "mar",
        "Gujarati" to "guj",
        "Punjabi" to "pan",
        "Urdu" to "urd",
        "Odia" to "ori",
        "Assamese" to "asm"
    )
    private val languageBase = "https://iptv-org.github.io/iptv/languages"
    private val languageMaxAgeMs = TimeUnit.DAYS.toMillis(7)

    private fun download(url: String): String =
        downloader.newCall(
            Request.Builder().url(url).header("User-Agent", DEFAULT_USER_AGENT).build()
        ).execute().use { r ->
            if (!r.isSuccessful) error("Server returned ${r.code}")
            r.body!!.string()
        }

    /** Downloads and parses a playlist; falls back to its last saved copy when offline. Each playlist has its own cache. */
    suspend fun load(playlist: Playlist): List<Channel> = withContext(Dispatchers.IO) {
        val cache = File(dir, "playlist_${playlist.id}.m3u")
        val text = try {
            download(playlist.url).also { cache.writeText(it) }
        } catch (e: Exception) {
            if (cache.exists()) cache.readText() else throw e
        }
        val channels = M3uParser.parse(text)

        // The playlist already carries language tags: nothing more to do.
        if (channels.any { it.languages != listOf("Other") }) return@withContext channels

        // Otherwise tag channels using the per-language playlists, matched by stream URL.
        val index = languageIndex()
        if (index.isEmpty()) return@withContext channels
        channels.map { c ->
            index[c.url]?.let { c.copy(languages = it.distinct()) } ?: c
        }
    }

    /** stream URL -> languages. Each language file is cached for 7 days; stale copies are used if offline. */
    private suspend fun languageIndex(): Map<String, List<String>> = coroutineScope {
        languageFiles.map { (language, code) ->
            async {
                val text = languageFile(code)
                val urls = text?.let { M3uParser.parse(it) }?.map { it.url }.orEmpty()
                language to urls
            }
        }.awaitAll()
            .flatMap { (language, urls) -> urls.map { it to language } }
            .groupBy({ it.first }, { it.second })
    }

    private fun languageFile(code: String): String? {
        val f = File(dir, "lang_$code.m3u")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < languageMaxAgeMs
        if (fresh) return f.readText()
        return try {
            download("$languageBase/$code.m3u").also { f.writeText(it) }
        } catch (e: Exception) {
            if (f.exists()) f.readText() else null
        }
    }

    /**
     * Returns null when the channel should stay visible, or a short reason code when it is clearly dead.
     * Only definite failures count. A 403, a timeout or a TLS hiccup is NOT enough to hide a channel,
     * because many streams reject checkers but play fine in the player.
     * Codes: not_found, server, invalid, host, conn.
     */
    suspend fun failureReason(c: Channel, retry: Boolean): String? = withContext(Dispatchers.IO) {
        val first = probe(c)
        if (first == null || !retry) return@withContext first
        delay(1500)
        probe(c)
    }

    private fun probe(c: Channel): String? {
        val b = Request.Builder().url(c.url).header("User-Agent", c.userAgent ?: DEFAULT_USER_AGENT)
        c.referrer?.let { ref ->
            b.header("Referer", ref)
            originOf(ref)?.let { b.header("Origin", it) }
        }
        return try {
            checker.newCall(b.build()).execute().use { r ->
                when {
                    r.code == 404 || r.code == 410 -> "not_found"
                    r.code in 500..599 -> "server"
                    !r.isSuccessful -> null
                    c.url.contains(".m3u8", ignoreCase = true) -> {
                        val head = r.peekBody(512).string().trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                        if (head.isEmpty() || head.startsWith("#EXT")) null else "invalid"
                    }
                    else -> null
                }
            }
        } catch (e: UnknownHostException) {
            "host"
        } catch (e: ConnectException) {
            "conn"
        } catch (e: Exception) {
            null
        }
    }
}
