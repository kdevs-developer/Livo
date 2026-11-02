package com.kdevs.livo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PlaylistRepository(context: Context) {
    private val dir = context.filesDir
    private val cache = File(dir, "playlist.m3u")
    private val downloader = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val checker = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS).build()

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
        downloader.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("Server returned ${r.code}")
            r.body!!.string()
        }

    /** Downloads and parses the playlist; falls back to the last saved copy when offline. */
    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val text = try {
            download(url).also { cache.writeText(it) }
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

    /** Quick reachability test for one stream. */
    suspend fun isOnline(c: Channel): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(c.url).header("User-Agent", "Mozilla/5.0 (Livo)").build()
            checker.newCall(req).execute().use { r ->
                if (!r.isSuccessful) false
                else if (c.url.contains(".m3u8", ignoreCase = true)) r.peekBody(7).string().startsWith("#EXTM3U")
                else true
            }
        } catch (e: Exception) {
            false
        }
    }
}