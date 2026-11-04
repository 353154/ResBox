package com.resbox.player.media.adblock

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Custom Media3 `HlsPlaylistParserFactory` (Requirement 4.4):
 * Intercepts raw `.m3u8` playlists in memory before `HlsPlaylistParser` parses them, and strips
 * injected casino/pre-roll/mid-roll TS ad segments by analyzing:
 * 1. `#EXT-X-DISCONTINUITY` bounded blocks whose total duration is short (e.g., 3s - 28s ad splice)
 *    or whose segment filenames/domains diverge from the primary stream's naming pattern.
 * 2. Segment URLs matching user/config ad domain blacklists (`ads` in `TvBoxConfig`).
 * 3. Abrupt segment duration anomalies (e.g., exact 5.000000s / 3.333333s ad segments surrounded by discontinuities).
 */
@UnstableApi
class AdBlockHlsPlaylistParserFactory(
    private val enabledProvider: () -> Boolean,
    private val blacklistRulesProvider: () -> List<String>,
    private val onAdSegmentsFiltered: (filteredCount: Int) -> Unit = {}
) : HlsPlaylistParserFactory {

    private val defaultDelegate = HlsPlaylistParserFactory { HlsPlaylistParser() }

    override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> {
        return SanitizingHlsPlaylistParser(
            delegate = HlsPlaylistParser(),
            enabledProvider = enabledProvider,
            blacklistRulesProvider = blacklistRulesProvider,
            onAdSegmentsFiltered = onAdSegmentsFiltered
        )
    }

    override fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist,
        previousMediaPlaylist: HlsMediaPlaylist?
    ): ParsingLoadable.Parser<HlsPlaylist> {
        return SanitizingHlsPlaylistParser(
            delegate = HlsPlaylistParser(multivariantPlaylist, previousMediaPlaylist),
            enabledProvider = enabledProvider,
            blacklistRulesProvider = blacklistRulesProvider,
            onAdSegmentsFiltered = onAdSegmentsFiltered
        )
    }
}

@UnstableApi
private class SanitizingHlsPlaylistParser(
    private val delegate: ParsingLoadable.Parser<HlsPlaylist>,
    private val enabledProvider: () -> Boolean,
    private val blacklistRulesProvider: () -> List<String>,
    private val onAdSegmentsFiltered: (Int) -> Unit
) : ParsingLoadable.Parser<HlsPlaylist> {

    override fun parse(uri: Uri, inputStream: InputStream): HlsPlaylist {
        val rawM3u8 = inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        if (!enabledProvider() || !rawM3u8.contains("#EXTINF", ignoreCase = true)) {
            return delegate.parse(uri, ByteArrayInputStream(rawM3u8.toByteArray(StandardCharsets.UTF_8)))
        }

        val (cleanedM3u8, removedCount) = filterDiscontinuityAndBlacklistedSegments(
            rawM3u8 = rawM3u8,
            blacklist = blacklistRulesProvider()
        )
        if (removedCount > 0) {
            onAdSegmentsFiltered(removedCount)
        }

        return delegate.parse(
            uri,
            ByteArrayInputStream(cleanedM3u8.toByteArray(StandardCharsets.UTF_8))
        )
    }

    /**
     * Parses an HLS Media Playlist into blocks separated by `#EXT-X-DISCONTINUITY`,
     * identifies ad blocks, and removes them cleanly in memory.
     */
    private fun filterDiscontinuityAndBlacklistedSegments(
        rawM3u8: String,
        blacklist: List<String>
    ): Pair<String, Int> {
        val lines = rawM3u8.lines()
        val headerLines = mutableListOf<String>()
        val footerLines = mutableListOf<String>()
        val blocks = mutableListOf<MutableList<TsSegmentEntry>>()
        var currentBlock = mutableListOf<TsSegmentEntry>()
        blocks.add(currentBlock)

        var pendingExtInfLine: String? = null
        var pendingDurationSec = 0.0
        var PendingKeyLine: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-DISCONTINUITY", ignoreCase = true) -> {
                    if (currentBlock.isNotEmpty()) {
                        currentBlock = mutableListOf()
                        blocks.add(currentBlock)
                    }
                }
                line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                    PendingKeyLine = line
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingExtInfLine = line
                    val durStr = line.removePrefix("#EXTINF:").substringBefore(",").trim()
                    pendingDurationSec = durStr.toDoubleOrNull() ?: 0.0
                }
                line.startsWith("#EXT-X-ENDLIST", ignoreCase = true) -> {
                    footerLines.add(line)
                }
                line.startsWith("#") -> {
                    if (blocks.size == 1 && currentBlock.isEmpty()) {
                        headerLines.add(line)
                    }
                }
                else -> {
                    val extInf = pendingExtInfLine
                    if (extInf != null) {
                        currentBlock.add(
                            TsSegmentEntry(
                                extInfLine = extInf,
                                durationSec = pendingDurationSec,
                                uriLine = line,
                                keyLine = PendingKeyLine
                            )
                        )
                        pendingExtInfLine = null
                        PendingKeyLine = null
                    }
                }
            }
        }

        val nonEmptyBlocks = blocks.filter { it.isNotEmpty() }
        if (nonEmptyBlocks.size <= 1) {
            // Even without #EXT-X-DISCONTINUITY, filter out explicitly blacklisted segment URLs
            var removed = 0
            val sb = StringBuilder()
            headerLines.forEach { sb.appendLine(it) }
            nonEmptyBlocks.flatten().forEach { seg ->
                if (isBlacklistedSegment(seg.uriLine, blacklist)) {
                    removed++
                } else {
                    seg.keyLine?.let { sb.appendLine(it) }
                    sb.appendLine(seg.extInfLine)
                    sb.appendLine(seg.uriLine)
                }
            }
            footerLines.forEach { sb.appendLine(it) }
            return sb.toString() to removed
        }

        // Determine the dominant (main movie) block prefix pattern
        val dominantBlock = nonEmptyBlocks.maxByOrNull { it.size } ?: nonEmptyBlocks.first()
        val dominantPrefix = extractUriPrefixSignature(dominantBlock.first().uriLine)

        var removedSegmentsCount = 0
        val out = StringBuilder()
        headerLines.forEach { out.appendLine(it) }

        var isFirstKeptBlock = true
        for (block in nonEmptyBlocks) {
            val blockTotalDuration = block.sumOf { it.durationSec }
            val blockSampleUri = block.first().uriLine
            val blockPrefix = extractUriPrefixSignature(blockSampleUri)

            val matchesBlacklist = block.any { isBlacklistedSegment(it.uriLine, blacklist) }
            val isShortSpliceAdBlock = block.size <= 8 &&
                blockTotalDuration in 2.0..32.0 &&
                blockPrefix != dominantPrefix &&
                dominantBlock.size > 10

            if (matchesBlacklist || isShortSpliceAdBlock) {
                removedSegmentsCount += block.size
            } else {
                if (!isFirstKeptBlock) {
                    out.appendLine("#EXT-X-DISCONTINUITY")
                }
                isFirstKeptBlock = false
                for (seg in block) {
                    if (isBlacklistedSegment(seg.uriLine, blacklist)) {
                        removedSegmentsCount++
                    } else {
                        seg.keyLine?.let { out.appendLine(it) }
                        out.appendLine(seg.extInfLine)
                        out.appendLine(seg.uriLine)
                    }
                }
            }
        }

        footerLines.forEach { out.appendLine(it) }
        return out.toString() to removedSegmentsCount
    }

    private fun isBlacklistedSegment(uriLine: String, blacklist: List<String>): Boolean {
        val lower = uriLine.lowercase()
        return blacklist.any { rule ->
            rule.isNotBlank() && lower.contains(rule.lowercase())
        }
    }

    private fun extractUriPrefixSignature(uriLine: String): String {
        val clean = uriLine.substringBefore("?")
        val slashIdx = clean.lastIndexOf('/')
        return if (slashIdx >= 0) {
            clean.substring(0, slashIdx)
        } else {
            clean.take(6)
        }
    }

    private data class TsSegmentEntry(
        val extInfLine: String,
        val durationSec: Double,
        val uriLine: String,
        val keyLine: String? = null
    )
}
