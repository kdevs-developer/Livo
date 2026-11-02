package com.kdevs.livo.data
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

        val a = attr.findAll(info)
            .associate {
                it.groupValues[1].lowercase() to it.groupValues[2]
            }

        val lastQuote = info.lastIndexOf('"')
        val comma = info.indexOf(',', if (lastQuote >= 0) lastQuote else 0)

        val name = (if (comma >= 0) info.substring(comma + 1).trim() else "")
            .ifBlank { "Channel" }

        val languages = a["tvg-language"]
            .splitList()
            .map { language ->
                indianLanguages[language.lowercase()] ?: language
            }
            .distinct()
            .ifEmpty { listOf("Other") }

        val category = a["group-title"]
            .orEmpty()
            .split(';')
            .firstOrNull()
            ?.trim()
            .orEmpty()
            .let {
                if (it.isBlank() || it.equals("Undefined", true)) "Other" else it
            }

        return Channel(
            url = url,
            name = name,
            logo = a["tvg-logo"]?.takeIf { it.startsWith("http") },
            category = category,
            languages = languages
        )
    }

    private fun String?.splitList(): List<String> =
        this.orEmpty().split(';', ',').map { it.trim() }.filter { it.isNotBlank() }

    private val indianLanguages = mapOf(
        "en" to "English",
        "english" to "English",
        "hi" to "Hindi",
        "hindi" to "Hindi",
        "ta" to "Tamil",
        "tamil" to "Tamil",
        "te" to "Telugu",
        "telugu" to "Telugu",
        "ml" to "Malayalam",
        "malayalam" to "Malayalam",
        "kn" to "Kannada",
        "kannada" to "Kannada",
        "bn" to "Bengali",
        "bengali" to "Bengali",
        "mr" to "Marathi",
        "marathi" to "Marathi",
        "gu" to "Gujarati",
        "gujarati" to "Gujarati",
        "pa" to "Punjabi",
        "punjabi" to "Punjabi",
        "or" to "Odia",
        "odia" to "Odia",
        "as" to "Assamese",
        "assamese" to "Assamese",
        "ur" to "Urdu",
        "urdu" to "Urdu",
        "sa" to "Sanskrit",
        "sanskrit" to "Sanskrit",
        "ks" to "Kashmiri",
        "kashmiri" to "Kashmiri",
        "sd" to "Sindhi",
        "sindhi" to "Sindhi",
        "kok" to "Konkani",
        "konkani" to "Konkani",
        "doi" to "Dogri",
        "dogri" to "Dogri",
        "mni" to "Manipuri",
        "manipuri" to "Manipuri",
        "ne" to "Nepali",
        "nepali" to "Nepali",
        "sat" to "Santali",
        "santali" to "Santali",
        "brx" to "Bodo",
        "bodo" to "Bodo",
        "mai" to "Maithili",
        "maithili" to "Maithili"
    )

}
