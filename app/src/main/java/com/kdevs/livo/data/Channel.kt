package com.kdevs.livo.data

data class Channel(
    /** Unique per entry. Normally the stream URL; entries that share a URL get a suffix. */
    val id: String,
    val url: String,
    val name: String,
    val logo: String?,
    val category: String,
    val languages: List<String>,
    /** Position in the playlist, starting at 1. */
    val number: Int = 0,
    /** From #EXTVLCOPT http-referrer, needed by some streams. */
    val referrer: String? = null,
    /** From #EXTVLCOPT http-user-agent, needed by some streams. */
    val userAgent: String? = null,
)

data class Playlist(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean = false,
    /** Set for iptv-org language playlists: every channel in the list is tagged with this language. */
    val language: String? = null,
)