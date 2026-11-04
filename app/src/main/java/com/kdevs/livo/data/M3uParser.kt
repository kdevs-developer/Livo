package com.kdevs.livo.data
object M3uParser {
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        val seen = HashSet<String>()
        val perUrl = HashMap<String, Int>()
        var info: String? = null
        var referrer: String? = null
        var agent: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('\uFEFF')
            when {
                line.startsWith("#EXTINF") -> { info = line; referrer = null; agent = null }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim().ifBlank { null }
                    when (key) {
                        "http-referrer", "http-referer" -> referrer = value
                        "http-user-agent" -> agent = value
                    }
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                info != null -> {
                    build(info, line, referrer, agent, out.size + 1, perUrl)?.let {
                        // Same name AND same URL is a true duplicate. Same URL under a different name is kept.
                        if (seen.add(it.name + "\u0000" + it.url)) out.add(it)
                    }
                    info = null; referrer = null; agent = null
                }
            }
        }
        return out
    }

    private fun build(
        info: String, url: String, referrer: String?, agent: String?, number: Int,
        perUrl: HashMap<String, Int>,
    ): Channel? {
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

        val count = perUrl.merge(url, 1) { old, add -> old + add } ?: 1
        return Channel(
            id = if (count == 1) url else "$url#$count",
            url = url,
            name = name,
            logo = a["tvg-logo"]?.takeIf { it.startsWith("http") },
            category = category,
            languages = languages,
            number = number,
            referrer = referrer,
            userAgent = agent,
        )
    }

    private fun String?.splitList(): List<String> =
        this.orEmpty().split(';', ',', '/', '|').map { it.trim() }.filter { it.isNotBlank() }

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
