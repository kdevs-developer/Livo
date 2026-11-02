package com.kdevs.livo.data

data class Channel(
    val url: String,
    val name: String,
    val logo: String?,
    val category: String,
    val languages: List<String>
) {
    val id: String get() = url
}
