package com.kdevs.livo.data

import java.util.Locale

fun countryName(code: String): String =
    if (code.length == 2) Locale("", code).displayCountry.ifBlank { code } else code

object M3uParser {
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        val seen = HashSet<String>()
        var info: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                info = line
            } else if (line.isEmpty() || line.startsWith("#")) {
                continue
            } else if (info != null) {
                build(info, line)?.let { if (seen.add(it.url)) out.add(it) }
                info = null
            }
        }
        return out
    }

    private fun build(info: String, url: String): Channel? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        val a = attr.findAll(info).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val lastQuote = info.lastIndexOf('"')
        val comma = info.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
        val name = (if (comma >= 0) info.substring(comma + 1).trim() else "").ifBlank { "Channel" }

        var countries = a["tvg-country"].splitList().map { it.uppercase() }
        if (countries.isEmpty()) {
            val code = a["tvg-id"]?.substringAfterLast('.', "")?.substringBefore('@')?.uppercase().orEmpty()
            if (code.length == 2 && code.all { it.isLetter() }) countries = listOf(if (code == "UK") "GB" else code)
        }
        if (countries.isEmpty()) countries = listOf("Other")

        val languages = a["tvg-language"].splitList().ifEmpty { listOf("Other") }
        val category = a["group-title"].orEmpty().split(';').firstOrNull()?.trim().orEmpty()
            .let { if (it.isBlank() || it.equals("Undefined", true)) "Other" else it }

        return Channel(url, name, a["tvg-logo"]?.takeIf { it.startsWith("http") }, category, countries, languages)
    }

    private fun String?.splitList(): List<String> =
        this.orEmpty().split(';', ',').map { it.trim() }.filter { it.isNotBlank() }
}
