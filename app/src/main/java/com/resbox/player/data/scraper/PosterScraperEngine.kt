package com.resbox.player.data.scraper

import com.resbox.player.data.local.dao.PosterScrapDao
import com.resbox.player.data.local.datastore.AppPreferences
import com.resbox.player.data.local.datastore.SettingsDataStore
import com.resbox.player.data.local.entity.PosterScrapEntity
import com.resbox.player.data.remote.serializer.ResilientJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent Multi-Tier Poster & Metadata Scraper Engine for ResBox.
 *
 * Tier 1: In-Memory L1 Cache (Instant UI feedback)
 * Tier 2: Room SQLite L2 Cache (`poster_scrap_cache`)
 * Tier 3: TMDB Multi-Search API (Primary high-definition 4K poster & backdrop source)
 * Tier 4: Douban Public Search / Image mirror (Fallback for domestic C-Dramas & Variety shows)
 * Fallback: Gracefully falls back to the CMS source's original `vod_pic`
 */
@Singleton
class PosterScraperEngine @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val posterScrapDao: PosterScrapDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val memoryL1Cache = ConcurrentHashMap<String, ScrapedMetadata>()

    /**
     * Scrapes or retrieves high-definition posters (w780) and 16:9 backdrops (w1280) for any VOD item.
     */
    suspend fun scrapeMetadata(
        rawTitle: String,
        rawYear: String = "",
        fallbackPic: String = ""
    ): ScrapedMetadata = withContext(Dispatchers.IO) {
        if (rawTitle.isBlank()) {
            return@withContext ScrapedMetadata(posterUrl = fallbackPic)
        }

        val cleanResult = TitleSanitizer.clean(rawTitle)
        val cleanKey = cleanResult.cleanTitle.lowercase().trim()
        val targetYear = cleanResult.extractedYear.ifBlank { rawYear.trim().take(4) }

        // 1. Tier 1: In-Memory Cache
        memoryL1Cache[cleanKey]?.let { return@withContext it }

        // 2. Tier 2: Room Database L2 Cache
        val cachedEntity = posterScrapDao.getScrapByTitle(cleanKey)
        if (cachedEntity != null && cachedEntity.posterUrl.isNotBlank()) {
            val result = ScrapedMetadata(
                title = cleanResult.cleanTitle,
                posterUrl = cachedEntity.posterUrl,
                backdropUrl = cachedEntity.backdropUrl,
                ratingScore = cachedEntity.ratingScore,
                releaseYear = cachedEntity.releaseYear,
                overview = cachedEntity.overview,
                source = cachedEntity.source
            )
            memoryL1Cache[cleanKey] = result
            return@withContext result
        }

        // Check if user disabled scraping
        val prefs = settingsDataStore.preferencesFlow.first()
        if (!prefs.enablePosterScraping) {
            return@withContext ScrapedMetadata(
                title = cleanResult.cleanTitle,
                posterUrl = fallbackPic,
                source = "original"
            )
        }

        // 3. Tier 3: Fetch from TMDB API
        val apiKey = prefs.tmdbApiKey.ifBlank { AppPreferences.DEFAULT_TMDB_API_KEY }
        val tmdbResult = fetchFromTmdb(cleanResult.cleanTitle, targetYear, apiKey)
        if (tmdbResult != null) {
            saveAndCache(cleanKey, tmdbResult)
            return@withContext tmdbResult
        }

        // 4. Tier 4: Fetch from Douban Mirror Fallback (Specialized for Chinese Dramas & Anime)
        val doubanResult = fetchFromDoubanMirror(cleanResult.cleanTitle)
        if (doubanResult != null) {
            saveAndCache(cleanKey, doubanResult)
            return@withContext doubanResult
        }

        // 5. Final Fallback: Original source pic
        val fallbackResult = ScrapedMetadata(
            title = cleanResult.cleanTitle,
            posterUrl = fallbackPic,
            releaseYear = targetYear,
            source = "original"
        )
        memoryL1Cache[cleanKey] = fallbackResult
        fallbackResult
    }

    private fun fetchFromTmdb(title: String, year: String, apiKey: String): ScrapedMetadata? {
        return try {
            val baseUrl = "https://api.themoviedb.org/3/search/multi".toHttpUrlOrNull() ?: return null
            val urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("query", title)
                .addQueryParameter("language", "zh-CN")
                .addQueryParameter("include_adult", "false")

            if (year.length == 4 && year.all { it.isDigit() }) {
                urlBuilder.addQueryParameter("year", year)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "Mozilla/5.0 ResBox/1.0 TMDB-Client")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) return null

                val parsed = ResilientJson.decodeFromString(TmdbSearchResponse.serializer(), bodyStr)
                val candidates = parsed.results.filter {
                    it.posterPath != null && (it.mediaType == "movie" || it.mediaType == "tv")
                }
                if (candidates.isEmpty()) return null

                // Pick best match: match year or highest vote count
                val best = candidates.firstOrNull { item ->
                    val itemDate = item.releaseDate ?: item.firstAirDate
                    year.isNotBlank() && itemDate?.startsWith(year) == true
                } ?: candidates.first()

                val posterFull = "https://image.tmdb.org/t/p/w780${best.posterPath}"
                val backdropFull = if (best.backdropPath != null) {
                    "https://image.tmdb.org/t/p/w1280${best.backdropPath}"
                } else ""

                val vote = best.voteAverage
                val scoreStr = if (vote != null && vote > 0.0) {
                    String.format(Locale.US, "%.1f", vote)
                } else ""

                val matchedTitle = best.title ?: best.name ?: title
                val dateStr = best.releaseDate ?: best.firstAirDate ?: year

                ScrapedMetadata(
                    title = matchedTitle,
                    originalTitle = best.originalTitle ?: best.originalName.orEmpty(),
                    posterUrl = posterFull,
                    backdropUrl = backdropFull,
                    ratingScore = scoreStr,
                    releaseYear = dateStr.take(4),
                    overview = best.overview.orEmpty(),
                    source = "tmdb"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromDoubanMirror(title: String): ScrapedMetadata? {
        return try {
            // Public lightweight search endpoint for Chinese titles
            val url = "https://movie.douban.com/j/subject_suggest?q=${title}".toHttpUrlOrNull() ?: return null
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://movie.douban.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank() || !raw.startsWith("[")) return null

                val jsonArray = ResilientJson.parseToJsonElement(raw)
                val firstObj = jsonArray as? kotlinx.serialization.json.JsonArray
                val match = firstObj?.firstOrNull() as? kotlinx.serialization.json.JsonObject ?: return null

                val img = (match["img"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                val matchedTitle = (match["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: title
                val year = (match["year"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

                if (img.isNotBlank()) {
                    // Up-res Douban thumbnail: `s_ratio_poster` -> `l_ratio_poster` (High-Res 1080P poster)
                    val highResImg = img.replace("s_ratio_poster", "l_ratio_poster")
                        .replace("m_ratio_poster", "l_ratio_poster")
                    ScrapedMetadata(
                        title = matchedTitle,
                        posterUrl = highResImg,
                        releaseYear = year,
                        source = "douban"
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun saveAndCache(cleanKey: String, metadata: ScrapedMetadata) {
        memoryL1Cache[cleanKey] = metadata
        posterScrapDao.insertScrap(
            PosterScrapEntity(
                cleanTitle = cleanKey,
                releaseYear = metadata.releaseYear,
                posterUrl = metadata.posterUrl,
                backdropUrl = metadata.backdropUrl,
                ratingScore = metadata.ratingScore,
                overview = metadata.overview,
                source = metadata.source
            )
        )
    }
}
