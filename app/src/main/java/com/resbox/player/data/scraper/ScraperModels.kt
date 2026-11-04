package com.resbox.player.data.scraper

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
data class ScrapedMetadata(
    val title: String = "",
    val originalTitle: String = "",
    val posterUrl: String = "",      // TMDB w780 / Douban 高清竖版海报
    val backdropUrl: String = "",    // TMDB w1280 16:9 横版剧照背景
    val ratingScore: String = "",    // 精准评分 (如 8.8)
    val releaseYear: String = "",
    val overview: String = "",       // 官方详细剧情
    val genres: List<String> = emptyList(),
    val source: String = "original"  // "tmdb", "douban", "original"
)

// TMDB API Search Models
@Serializable
data class TmdbSearchResponse(
    @SerialName("page")
    val page: Int = 1,
    @SerialName("results")
    val results: List<TmdbItemDto> = emptyList()
)

@Serializable
data class TmdbItemDto(
    @SerialName("id")
    val id: Long = 0L,
    @SerialName("media_type")
    val mediaType: String = "", // "movie" or "tv"
    @SerialName("title")
    val title: String? = null,
    @SerialName("name")
    val name: String? = null, // for TV shows
    @SerialName("original_title")
    val originalTitle: String? = null,
    @SerialName("original_name")
    val originalName: String? = null,
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerialName("backdrop_path")
    val backdropPath: String? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("first_air_date")
    val firstAirDate: String? = null,
    @SerialName("overview")
    val overview: String? = null
)
