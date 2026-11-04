package com.resbox.player.data.model

import androidx.compose.runtime.Immutable
import com.resbox.player.data.remote.serializer.FlexibleExtSerializer
import com.resbox.player.data.remote.serializer.FlexibleIntSerializer
import com.resbox.player.data.remote.serializer.FlexibleStringListSerializer
import com.resbox.player.data.remote.serializer.FlexibleStringMapSerializer
import com.resbox.player.data.remote.serializer.HtmlCleanStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Multi-Repository (`urls` or `storeHouse`) root configuration model.
 */
@Immutable
@Serializable
data class MultiRepoConfig(
    @SerialName("urls")
    val urls: List<RepoEntry> = emptyList(),
    @SerialName("storeHouse")
    val storeHouse: List<RepoEntry> = emptyList()
) {
    val allEntries: List<RepoEntry>
        get() = (urls + storeHouse).distinctBy { it.url }
}

@Immutable
@Serializable
data class RepoEntry(
    @SerialName("sourceName")
    val name: String = "未命名仓库",
    @SerialName("sourceUrl")
    val url: String = ""
)

/**
 * Standard single-line TVBox / CatVod JSON configuration.
 */
@Immutable
@Serializable
data class TvBoxConfig(
    @SerialName("spider")
    val spider: String = "",
    @SerialName("wallpaper")
    val wallpaper: String = "",
    @SerialName("sites")
    val sites: List<SiteBean> = emptyList(),
    @SerialName("parses")
    val parses: List<ParseBean> = emptyList(),
    @SerialName("lives")
    val lives: List<LiveBean> = emptyList(),
    @SerialName("flags")
    @Serializable(with = FlexibleStringListSerializer::class)
    val flags: List<String> = emptyList(),
    @SerialName("ads")
    @Serializable(with = FlexibleStringListSerializer::class)
    val ads: List<String> = emptyList()
)

/**
 * TVBox Site node (`SiteBean`).
 * `type`: 0 = MacCMS XML, 1 = MacCMS JSON, 3 = Jar/JS Spider (`csp_*`), 4 = Remote HTTP Spider.
 */
@Immutable
@Serializable
data class SiteBean(
    @SerialName("key")
    val key: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("type")
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 1,
    @SerialName("api")
    val api: String = "",
    @SerialName("searchable")
    @Serializable(with = FlexibleIntSerializer::class)
    val searchable: Int = 1,
    @SerialName("quickSearch")
    @Serializable(with = FlexibleIntSerializer::class)
    val quickSearch: Int = 1,
    @SerialName("filterable")
    @Serializable(with = FlexibleIntSerializer::class)
    val filterable: Int = 1,
    @SerialName("playerType")
    @Serializable(with = FlexibleIntSerializer::class)
    val playerType: Int = 0,
    @SerialName("ext")
    @Serializable(with = FlexibleExtSerializer::class)
    val ext: String = "",
    @SerialName("jar")
    val jar: String = "",
    @SerialName("playUrl")
    val playUrl: String = "",
    @SerialName("categories")
    @Serializable(with = FlexibleStringListSerializer::class)
    val categories: List<String> = emptyList(),
    @SerialName("timeout")
    @Serializable(with = FlexibleIntSerializer::class)
    val timeout: Int = 15
) {
    val isSearchable: Boolean get() = searchable == 1
    val isSpiderSite: Boolean get() = type == 3 || api.startsWith("csp_")
}

/**
 * TVBox Parser node (`ParseBean`).
 * `type`: 0 = WebView Sniffer, 1 = JSON API Parser, 2 = Parallel Aggregator, 3 = Aggregate Sniffer.
 */
@Immutable
@Serializable
data class ParseBean(
    @SerialName("name")
    val name: String = "",
    @SerialName("type")
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0,
    @SerialName("url")
    val url: String = "",
    @SerialName("ext")
    @Serializable(with = FlexibleExtSerializer::class)
    val ext: String = "",
    @SerialName("header")
    @Serializable(with = FlexibleStringMapSerializer::class)
    val headers: Map<String, String> = emptyMap()
)

/**
 * TVBox Live TV configuration (`LiveBean`).
 */
@Immutable
@Serializable
data class LiveBean(
    @SerialName("name")
    val name: String = "电视直播",
    @SerialName("type")
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0,
    @SerialName("url")
    val url: String = "",
    @SerialName("epg")
    val epg: String = "",
    @SerialName("logo")
    val logo: String = "",
    @SerialName("ua")
    val ua: String = "",
    @SerialName("playerType")
    @Serializable(with = FlexibleIntSerializer::class)
    val playerType: Int = 0,
    @SerialName("channels")
    val channels: List<LiveChannelGroup> = emptyList()
)

@Immutable
@Serializable
data class LiveChannelGroup(
    @SerialName("group")
    val group: String = "默认频道",
    @SerialName("channels")
    val channels: List<LiveChannelItem> = emptyList()
)

@Immutable
@Serializable
data class LiveChannelItem(
    @SerialName("name")
    val name: String = "",
    @SerialName("urls")
    @Serializable(with = FlexibleStringListSerializer::class)
    val urls: List<String> = emptyList(),
    val logoUrl: String = "",
    val tvgId: String = "",
    val catchupSource: String = ""
)

/**
 * Category item in `homeContent`.
 */
@Immutable
@Serializable
data class CategoryBean(
    @SerialName("type_id")
    @Serializable(with = FlexibleExtSerializer::class)
    val typeId: String = "",
    @SerialName("type_name")
    @Serializable(with = HtmlCleanStringSerializer::class)
    val typeName: String = ""
)

/**
 * Core VOD item (`VodInfo`) returned by `homeContent`, `categoryContent`, `detailContent`, and `searchContent`.
 * Parses `vod_play_from` (`$$$` separated) and `vod_play_url` (`$$$` and `#` and `$` separated) into structured sources.
 */
@Immutable
@Serializable
data class VodInfo(
    @SerialName("vod_id")
    @Serializable(with = FlexibleExtSerializer::class)
    val vodId: String = "",
    @SerialName("vod_name")
    @Serializable(with = HtmlCleanStringSerializer::class)
    val vodName: String = "",
    @SerialName("vod_pic")
    val vodPic: String = "",
    @SerialName("vod_remarks")
    @Serializable(with = HtmlCleanStringSerializer::class)
    val vodRemarks: String = "",
    @SerialName("vod_year")
    @Serializable(with = FlexibleExtSerializer::class)
    val vodYear: String = "",
    @SerialName("vod_area")
    @Serializable(with = HtmlCleanStringSerializer::class)
    val vodArea: String = "",
    @SerialName("vod_actor")
    @Serializable(with = HtmlCleanStringSerializer::class)
    val vodActor: String = "",
    @SerialName("vod_director")
    @Serializable(with = HtmlCleanStringSerializer::class)
    val vodDirector: String = "",
    @SerialName("vod_content")
    @Serializable(with = HtmlCleanStringSerializer::class)
    val vodContent: String = "",
    @SerialName("vod_play_from")
    val vodPlayFrom: String = "",
    @SerialName("vod_play_url")
    val vodPlayUrl: String = "",
    @SerialName("type_name")
    val typeName: String = "",
    @SerialName("vod_score")
    @Serializable(with = FlexibleExtSerializer::class)
    val vodScore: String = "",
    val siteKey: String = "",
    val siteName: String = "",
    val latencyMs: Long = -1L,
    val scrapedPoster: String = "",
    val scrapedBackdrop: String = ""
) {
    val displayPoster: String
        get() = scrapedPoster.ifBlank { vodPic }

    val displayBackdrop: String
        get() = scrapedBackdrop.ifBlank { displayPoster }

    /**
     * Parses TVBox standard `vod_play_from` ("线路A$$$线路B") and `vod_play_url` ("第1集$url1#第2集$url2$$$第1集$url3")
     * into strongly-typed `VodPlaySource` list.
     */
    fun parsePlaySources(): List<VodPlaySource> {
        if (vodPlayUrl.isBlank()) return emptyList()
        val fromFlags = if (vodPlayFrom.isBlank()) {
            listOf(siteName.ifBlank { "默认线路" })
        } else {
            vodPlayFrom.split("$$$").map { it.trim().ifEmpty { "默认线路" } }
        }
        val urlGroups = vodPlayUrl.split("$$$")
        return urlGroups.mapIndexedNotNull { index, groupRaw ->
            val flagName = fromFlags.getOrElse(index) { "线路 ${index + 1}" }
            val episodes = groupRaw.split("#")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { epIndex, epRaw ->
                    val dollarIdx = epRaw.indexOf('$')
                    if (dollarIdx >= 0) {
                        val title = epRaw.substring(0, dollarIdx).trim().ifEmpty { "第${epIndex + 1}集" }
                        val url = epRaw.substring(dollarIdx + 1).trim()
                        VodEpisode(title = title, url = url, sourceFlag = flagName, index = epIndex)
                    } else {
                        VodEpisode(title = "正片 ${epIndex + 1}", url = epRaw, sourceFlag = flagName, index = epIndex)
                    }
                }
                .filter { it.url.isNotEmpty() }

            if (episodes.isEmpty()) null else VodPlaySource(
                flag = flagName,
                siteKey = siteKey,
                siteName = siteName,
                episodes = episodes,
                latencyMs = latencyMs
            )
        }
    }

    val qualityBadge: String
        get() = when {
            vodRemarks.contains("4K", ignoreCase = true) || vodName.contains("4K", ignoreCase = true) -> "4K HDR"
            vodRemarks.contains("1080", ignoreCase = true) -> "1080P"
            vodRemarks.isNotBlank() -> vodRemarks.take(8)
            else -> "HD"
        }
}

@Immutable
data class VodPlaySource(
    val flag: String,
    val siteKey: String,
    val siteName: String,
    val episodes: List<VodEpisode>,
    val latencyMs: Long = -1L
)

@Immutable
data class VodEpisode(
    val title: String,
    val url: String,
    val sourceFlag: String,
    val index: Int
)

@Immutable
@Serializable
data class VodResponsePayload(
    @SerialName("class")
    val categories: List<CategoryBean> = emptyList(),
    @SerialName("list")
    val list: List<VodInfo> = emptyList(),
    @SerialName("page")
    @Serializable(with = FlexibleIntSerializer::class)
    val page: Int = 1,
    @SerialName("pagecount")
    @Serializable(with = FlexibleIntSerializer::class)
    val pageCount: Int = 1,
    @SerialName("limit")
    @Serializable(with = FlexibleIntSerializer::class)
    val limit: Int = 20,
    @SerialName("total")
    @Serializable(with = FlexibleIntSerializer::class)
    val total: Int = 0
)

/**
 * Standard response from `Spider.playerContent`.
 */
@Immutable
@Serializable
data class PlayerContentResult(
    @SerialName("parse")
    @Serializable(with = FlexibleIntSerializer::class)
    val parse: Int = 0,
    @SerialName("jx")
    @Serializable(with = FlexibleIntSerializer::class)
    val jx: Int = 0,
    @SerialName("url")
    @Serializable(with = FlexibleExtSerializer::class)
    val url: String = "",
    @SerialName("playUrl")
    val playUrl: String = "",
    @SerialName("header")
    @Serializable(with = FlexibleStringMapSerializer::class)
    val headers: Map<String, String> = emptyMap(),
    @SerialName("danmaku")
    val danmakuUrl: String = "",
    @SerialName("format")
    val format: String = ""
) {
    val requiresSniffing: Boolean
        get() = parse == 1 || jx == 1 || (!isDirectMediaUrl(url) && url.startsWith("http"))

    companion object {
        fun isDirectMediaUrl(candidate: String): Boolean {
            val lower = candidate.lowercase()
            return lower.contains(".m3u8") ||
                lower.contains(".mp4") ||
                lower.contains(".flv") ||
                lower.contains(".mkv") ||
                lower.contains(".mpd") ||
                lower.startsWith("rtmp://") ||
                lower.startsWith("rtsp://")
        }
    }
}
