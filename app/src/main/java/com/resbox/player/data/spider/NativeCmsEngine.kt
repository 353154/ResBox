package com.resbox.player.data.spider

import android.util.Xml
import com.resbox.player.data.model.CategoryBean
import com.resbox.player.data.model.SiteBean
import com.resbox.player.data.model.VodInfo
import com.resbox.player.data.model.VodResponsePayload
import com.resbox.player.data.remote.serializer.HtmlCleanStringSerializer
import com.resbox.player.data.remote.serializer.ResilientJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native TVBox Engine supporting:
 * 1. MacCMS v10 JSON API (`type = 1`)
 * 2. MacCMS XML API (`type = 0`)
 * 3. Jsoup HTML/XPath Rule Engine (`csp_XPath` / `csp_XYQ` fallback)
 * 4. Built-in Offline Showcase Data (ensuring instant rich experience even when offline)
 */
@Singleton
class NativeCmsEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    suspend fun fetchHomeContent(site: SiteBean): VodResponsePayload = withContext(Dispatchers.IO) {
        if (site.api.startsWith("resbox://showcase")) {
            return@withContext BuiltinShowcaseData.getHomePayload(site)
        }
        val baseUrl = site.api.toHttpUrlOrNull() ?: return@withContext BuiltinShowcaseData.getHomePayload(site)
        val url = baseUrl.newBuilder()
            .addQueryParameter("ac", "videolist")
            .addQueryParameter("pg", "1")
            .build()

        executeCmsRequest(site, url.toString())
    }

    suspend fun fetchCategoryContent(
        site: SiteBean,
        typeId: String,
        page: Int
    ): VodResponsePayload = withContext(Dispatchers.IO) {
        if (site.api.startsWith("resbox://showcase")) {
            return@withContext BuiltinShowcaseData.getCategoryPayload(site, typeId, page)
        }
        val baseUrl = site.api.toHttpUrlOrNull()
            ?: return@withContext BuiltinShowcaseData.getCategoryPayload(site, typeId, page)
        val url = baseUrl.newBuilder()
            .addQueryParameter("ac", "videolist")
            .addQueryParameter("t", typeId)
            .addQueryParameter("pg", page.toString())
            .build()

        executeCmsRequest(site, url.toString())
    }

    suspend fun fetchDetailContent(
        site: SiteBean,
        vodId: String
    ): VodInfo? = withContext(Dispatchers.IO) {
        if (site.api.startsWith("resbox://showcase")) {
            return@withContext BuiltinShowcaseData.getDetailVod(site, vodId)
        }
        val baseUrl = site.api.toHttpUrlOrNull()
            ?: return@withContext BuiltinShowcaseData.getDetailVod(site, vodId)
        val url = baseUrl.newBuilder()
            .addQueryParameter("ac", "videolist")
            .addQueryParameter("ids", vodId)
            .build()

        val payload = executeCmsRequest(site, url.toString())
        payload.list.firstOrNull()?.copy(siteKey = site.key, siteName = site.name)
    }

    suspend fun fetchSearchContent(
        site: SiteBean,
        keyword: String
    ): List<VodInfo> = withContext(Dispatchers.IO) {
        if (site.api.startsWith("resbox://showcase")) {
            return@withContext BuiltinShowcaseData.searchVod(site, keyword)
        }
        val baseUrl = site.api.toHttpUrlOrNull()
            ?: return@withContext BuiltinShowcaseData.searchVod(site, keyword)
        val url = baseUrl.newBuilder()
            .addQueryParameter("ac", "videolist")
            .addQueryParameter("wd", keyword)
            .build()

        executeCmsRequest(site, url.toString()).list.map {
            it.copy(siteKey = site.key, siteName = site.name)
        }
    }

    private fun executeCmsRequest(site: SiteBean, requestUrl: String): VodResponsePayload {
        return try {
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; TVBox) ResBox/1.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty().trim()
                if (bodyStr.isEmpty()) return VodResponsePayload()

                if (site.type == 0 || bodyStr.startsWith("<")) {
                    parseMacCmsXml(bodyStr, site)
                } else {
                    val parsed = ResilientJson.decodeFromString(VodResponsePayload.serializer(), bodyStr)
                    val filteredCategories = if (site.categories.isNotEmpty()) {
                        parsed.categories.filter { cat ->
                            site.categories.any { allowed -> cat.typeName.contains(allowed) }
                        }
                    } else {
                        parsed.categories
                    }
                    parsed.copy(
                        categories = filteredCategories,
                        list = parsed.list.map { it.copy(siteKey = site.key, siteName = site.name) }
                    )
                }
            }
        } catch (_: Exception) {
            VodResponsePayload()
        }
    }

    /**
     * Parses Apple CMS (`MacCMS`) XML feeds (`<rss><list><video>...`) into `VodResponsePayload`.
     */
    fun parseMacCmsXml(xmlContent: String, site: SiteBean): VodResponsePayload {
        val categories = mutableListOf<CategoryBean>()
        val vodList = mutableListOf<VodInfo>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var currentTag = ""
            var vodId = ""
            var vodName = ""
            var vodPic = ""
            var vodRemarks = ""
            var vodYear = ""
            var vodArea = ""
            var vodActor = ""
            var vodDirector = ""
            var vodContent = ""
            var typeName = ""
            val playFromList = mutableListOf<String>()
            val playUrlList = mutableListOf<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name ?: ""
                        if (currentTag.equals("ty", ignoreCase = true)) {
                            val idAttr = parser.getAttributeValue(null, "id").orEmpty()
                            val text = parser.nextText().orEmpty()
                            if (idAttr.isNotBlank() && text.isNotBlank()) {
                                categories.add(CategoryBean(typeId = idAttr, typeName = text))
                            }
                        } else if (currentTag.equals("video", ignoreCase = true)) {
                            vodId = ""
                            vodName = ""
                            vodPic = ""
                            vodRemarks = ""
                            vodYear = ""
                            vodArea = ""
                            vodActor = ""
                            vodDirector = ""
                            vodContent = ""
                            typeName = ""
                            playFromList.clear()
                            playUrlList.clear()
                        } else if (currentTag.equals("dd", ignoreCase = true)) {
                            val flagAttr = parser.getAttributeValue(null, "flag").orEmpty().ifBlank { site.name }
                            val rawUrls = parser.nextText().orEmpty().trim()
                            if (rawUrls.isNotBlank()) {
                                playFromList.add(flagAttr)
                                playUrlList.add(rawUrls)
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag.lowercase()) {
                                "id" -> vodId = text
                                "name" -> vodName = HtmlCleanStringSerializer.cleanHtmlText(text)
                                "pic" -> vodPic = text
                                "note" -> vodRemarks = text
                                "year" -> vodYear = text
                                "area" -> vodArea = text
                                "actor" -> vodActor = text
                                "director" -> vodDirector = text
                                "des" -> vodContent = HtmlCleanStringSerializer.cleanHtmlText(text)
                                "type" -> typeName = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("video", ignoreCase = true) && vodId.isNotBlank()) {
                            vodList.add(
                                VodInfo(
                                    vodId = vodId,
                                    vodName = vodName,
                                    vodPic = vodPic,
                                    vodRemarks = vodRemarks,
                                    vodYear = vodYear,
                                    vodArea = vodArea,
                                    vodActor = vodActor,
                                    vodDirector = vodDirector,
                                    vodContent = vodContent,
                                    vodPlayFrom = playFromList.joinToString("$$$"),
                                    vodPlayUrl = playUrlList.joinToString("$$$"),
                                    typeName = typeName,
                                    siteKey = site.key,
                                    siteName = site.name
                                )
                            )
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Fall through with collected items
        }
        return VodResponsePayload(categories = categories, list = vodList)
    }

    /**
     * Jsoup HTML parser for XPath/CSS selector spider rules.
     */
    fun parseHtmlCardsWithJsoup(
        html: String,
        itemSelector: String,
        titleSelector: String,
        linkSelector: String,
        picSelector: String,
        remarkSelector: String,
        site: SiteBean
    ): List<VodInfo> {
        return try {
            val doc = Jsoup.parse(html)
            doc.select(itemSelector).mapNotNull { el ->
                val title = el.selectFirst(titleSelector)?.text()?.trim().orEmpty()
                val href = el.selectFirst(linkSelector)?.attr("href")?.trim().orEmpty()
                val pic = el.selectFirst(picSelector)?.let {
                    it.attr("data-original").ifBlank { it.attr("data-src").ifBlank { it.attr("src") } }
                }.orEmpty()
                val remark = if (remarkSelector.isNotBlank()) {
                    el.selectFirst(remarkSelector)?.text()?.trim().orEmpty()
                } else ""

                if (title.isEmpty() || href.isEmpty()) null else VodInfo(
                    vodId = href,
                    vodName = title,
                    vodPic = pic,
                    vodRemarks = remark,
                    siteKey = site.key,
                    siteName = site.name
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
