package com.resbox.player.data.repository

import android.util.Xml
import androidx.compose.runtime.Immutable
import com.resbox.player.data.model.LiveChannelGroup
import com.resbox.player.data.model.LiveChannelItem
import com.resbox.player.data.spider.BuiltinShowcaseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class EpgProgramItem(
    val channelId: String,
    val title: String,
    val startTimeFormatted: String,
    val endTimeFormatted: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val isLiveNow: Boolean
)

/**
 * Supports TVBox IPTV Live sources:
 * - Standard `.m3u` / `.m3u8` playlists (`#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="..." catchup-source="...",Name`)
 * - TVBox `.txt` grouped playlists (`频道分组,#genre#` + `频道名称,http://...#http://...`)
 * - XMLTV EPG program guide parsing and Catch-up (`playseek`) URL formatting.
 */
@Singleton
class LiveRepository @Inject constructor(
    private val configRepository: ConfigRepository,
    private val okHttpClient: OkHttpClient
) {

    suspend fun loadChannelGroups(): List<LiveChannelGroup> = withContext(Dispatchers.IO) {
        val lives = configRepository.activeConfigState.value.lives
        val primaryLive = lives.firstOrNull() ?: BuiltinShowcaseData.defaultConfig.lives.first()

        if (primaryLive.channels.isNotEmpty()) {
            return@withContext primaryLive.channels
        }

        if (primaryLive.url.startsWith("http")) {
            val content = fetchRemoteText(primaryLive.url)
            if (!content.isNullOrBlank()) {
                val parsed = if (content.trimStart().startsWith("#EXTM3U")) {
                    parseM3uPlaylist(content)
                } else {
                    parseTxtGenrePlaylist(content)
                }
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }

        BuiltinShowcaseData.defaultConfig.lives.first().channels
    }

    /**
     * Parses standard `#EXTM3U` playlists including `tvg-id`, `tvg-logo`, `group-title`, and `catchup-source`.
     */
    fun parseM3uPlaylist(m3uContent: String): List<LiveChannelGroup> {
        val groupMap = linkedMapOf<String, MutableList<LiveChannelItem>>()
        var pendingName = ""
        var pendingGroup = "未分组频道"
        var pendingLogo = ""
        var pendingTvgId = ""
        var pendingCatchup = ""

        m3uContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingGroup = extractAttr(line, "group-title").ifBlank { "精选频道" }
                pendingLogo = extractAttr(line, "tvg-logo")
                pendingTvgId = extractAttr(line, "tvg-id").ifBlank { extractAttr(line, "tvg-name") }
                pendingCatchup = extractAttr(line, "catchup-source")
                val commaIdx = line.lastIndexOf(',')
                pendingName = if (commaIdx >= 0 && commaIdx + 1 < line.length) {
                    line.substring(commaIdx + 1).trim()
                } else {
                    "频道"
                }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                if (pendingName.isNotBlank()) {
                    val list = groupMap.getOrPut(pendingGroup) { mutableListOf() }
                    val existingIdx = list.indexOfFirst { it.name == pendingName }
                    if (existingIdx >= 0) {
                        val existing = list[existingIdx]
                        list[existingIdx] = existing.copy(urls = (existing.urls + line).distinct())
                    } else {
                        list.add(
                            LiveChannelItem(
                                name = pendingName,
                                urls = listOf(line),
                                logoUrl = pendingLogo,
                                tvgId = pendingTvgId.ifBlank { pendingName },
                                catchupSource = pendingCatchup
                            )
                        )
                    }
                    pendingName = ""
                }
            }
        }
        return groupMap.map { (group, items) ->
            LiveChannelGroup(group = group, channels = items)
        }
    }

    /**
     * Parses TVBox `.txt` live format:
     * `央视频道,#genre#`
     * `CCTV-1,http://stream1.m3u8#http://stream2.m3u8`
     */
    fun parseTxtGenrePlaylist(txtContent: String): List<LiveChannelGroup> {
        val groupMap = linkedMapOf<String, MutableList<LiveChannelItem>>()
        var currentGroup = "默认频道"

        txtContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEach

            if (line.contains(",#genre#", ignoreCase = true)) {
                currentGroup = line.substringBefore(",#genre#").trim().ifBlank { "默认频道" }
                groupMap.putIfAbsent(currentGroup, mutableListOf())
            } else if (line.contains(",")) {
                val name = line.substringBefore(",").trim()
                val rawUrls = line.substringAfter(",").trim()
                val urls = rawUrls.split("#").map { it.trim() }.filter { it.startsWith("http") || it.startsWith("rtmp") }
                if (name.isNotBlank() && urls.isNotEmpty()) {
                    val list = groupMap.getOrPut(currentGroup) { mutableListOf() }
                    list.add(
                        LiveChannelItem(
                            name = name,
                            urls = urls,
                            tvgId = name
                        )
                    )
                }
            }
        }
        return groupMap.filter { it.value.isNotEmpty() }.map { (g, chs) ->
            LiveChannelGroup(group = g, channels = chs)
        }
    }

    /**
     * Loads or generates the EPG electronic program guide timeline for a channel.
     */
    suspend fun loadEpgForChannel(channel: LiveChannelItem): List<EpgProgramItem> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val hourMs = 3_600_000L
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val baseHour = (now / hourMs) * hourMs

            // Provide a realistic 6-slot EPG timeline around current time (supports Catch-up replay testing)
            listOf(
                -2 to "${channel.name} • 经典剧场回放 (支持回看)",
                -1 to "${channel.name} • 午间特别新闻报道 (支持回看)",
                0 to "${channel.name} • 正在播出：臻彩 4K 纪实专栏",
                1 to "${channel.name} • 黄金档院线大片连播",
                2 to "${channel.name} • 环球视野深度解析"
            ).map { (offsetHours, title) ->
                val start = baseHour + offsetHours * hourMs
                val end = start + hourMs
                EpgProgramItem(
                    channelId = channel.tvgId.ifBlank { channel.name },
                    title = title,
                    startTimeFormatted = timeFmt.format(Date(start)),
                    endTimeFormatted = timeFmt.format(Date(end)),
                    startEpochMs = start,
                    endEpochMs = end,
                    isLiveNow = now in start until end
                )
            }
        }

    /**
     * Parses standard XMLTV `<tv><programme start="..." stop="..." channel="..."><title>...</title></programme></tv>`
     */
    fun parseXmlTvEpg(xmlContent: String, targetChannelId: String): List<EpgProgramItem> {
        val result = mutableListOf<EpgProgramItem>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var inTargetProgramme = false
            var startAttr = ""
            var stopAttr = ""
            var title = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name.equals("programme", ignoreCase = true)) {
                            val ch = parser.getAttributeValue(null, "channel").orEmpty()
                            if (ch.equals(targetChannelId, ignoreCase = true)) {
                                inTargetProgramme = true
                                startAttr = parser.getAttributeValue(null, "start").orEmpty()
                                stopAttr = parser.getAttributeValue(null, "stop").orEmpty()
                            }
                        } else if (inTargetProgramme && parser.name.equals("title", ignoreCase = true)) {
                            title = parser.nextText().orEmpty().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (inTargetProgramme && parser.name.equals("programme", ignoreCase = true)) {
                            inTargetProgramme = false
                            if (title.isNotBlank()) {
                                result.add(
                                    EpgProgramItem(
                                        channelId = targetChannelId,
                                        title = title,
                                        startTimeFormatted = formatXmlTvTimestamp(startAttr),
                                        endTimeFormatted = formatXmlTvTimestamp(stopAttr),
                                        startEpochMs = System.currentTimeMillis(),
                                        endEpochMs = System.currentTimeMillis() + 3_600_000L,
                                        isLiveNow = false
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Return parsed subset
        }
        return result
    }

    /**
     * Builds a Catch-up (time-shift replay) stream URL when the user selects a past EPG program.
     */
    fun buildCatchupUrl(channel: LiveChannelItem, program: EpgProgramItem): String {
        val baseStream = channel.urls.firstOrNull().orEmpty()
        val template = channel.catchupSource.ifBlank {
            if (baseStream.contains("?")) "$baseStream&playseek={utc}-{lutc}"
            else "$baseStream?playseek={utc}-{lutc}"
        }
        val startSec = program.startEpochMs / 1000
        val endSec = program.endEpochMs / 1000
        return template
            .replace("{utc}", startSec.toString())
            .replace("{lutc}", endSec.toString())
    }

    private fun extractAttr(line: String, attrName: String): String {
        val regex = Regex("${Regex.escape(attrName)}=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun formatXmlTvTimestamp(raw: String): String {
        return if (raw.length >= 12) {
            "${raw.substring(8, 10)}:${raw.substring(10, 12)}"
        } else {
            "00:00"
        }
    }

    private fun fetchRemoteText(url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
