package com.resbox.player.data.scraper

/**
 * Normalizes dirty/decorated titles commonly returned by third-party CMS sites
 * (e.g. "繁花 [4K杜比视界][全30集完][国语中字]" -> "繁花")
 * for accurate matching against TMDB and Douban metadata databases.
 */
object TitleSanitizer {

    private val BRACKET_PATTERN = Regex(
        pattern = "\\[.*?\\]|\\(.*?\\)|【.*?】|（.*?）",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val NOISE_WORDS_PATTERN = Regex(
        pattern = "(4k|1080p|720p|2160p|uhd|fhd|hd|bd|web-dl|bluray|蓝光|杜比视界|杜比音效|dolby|hdr10\\+?|dts|x264|x265|hevc|抢先版|TC版|TC|TS|枪版|未删减版?|无删减|国粤双语|国语|粤语|中字|双字|英语|特效中字|全集|全\\d+集|完结|更新至\\d+集?|第[0-9一二三四五六七八九十]+集|EP?\\d+|Season\\s*\\d+|S\\d+)",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val YEAR_PATTERN = Regex("(19\\d{2}|20\\d{2})")

    data class CleanResult(
        val cleanTitle: String,
        val extractedYear: String = ""
    )

    fun clean(rawTitle: String): CleanResult {
        var text = rawTitle.trim()

        // 1. Try to extract 4-digit release year if present
        val yearMatch = YEAR_PATTERN.find(text)
        val extractedYear = yearMatch?.value.orEmpty()

        // 2. Remove bracketed noise: [4K], (国语)
        text = text.replace(BRACKET_PATTERN, " ")

        // 3. Remove common technical & release tags
        text = text.replace(NOISE_WORDS_PATTERN, " ")

        // 4. Clean extra spaces, punctuation, underscores, and dots
        val cleaned = text
            .replace(Regex("[_\\-\\.\\/\\|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val finalTitle = cleaned.ifBlank { rawTitle.trim() }
        return CleanResult(cleanTitle = finalTitle, extractedYear = extractedYear)
    }
}
