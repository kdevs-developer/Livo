package com.kdevs.livo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PlaylistRepository(context: Context) {
    private val cache = File(context.filesDir, "playlist.m3u")
    private val downloader = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val checker = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS).build()

    /** Downloads and parses the playlist; falls back to the last saved copy when offline. */
    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val text = try {
            downloader.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("Server returned ${r.code}")
                r.body!!.string()
            }.also { cache.writeText(it) }
        } catch (e: Exception) {
            if (cache.exists()) cache.readText() else throw e
        }
        M3uParser.parse(text)
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
