package com.resbox.player.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val compositeId: String, // "${siteKey}_${vodId}"
    val siteKey: String,
    val siteName: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val sourceFlag: String,
    val episodeTitle: String,
    val episodeUrl: String,
    val episodeIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val skipIntroMs: Long = 0L,
    val skipOutroMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "vod_favorites")
data class FavoriteVodEntity(
    @PrimaryKey
    val compositeId: String, // "${siteKey}_${vodId}"
    val siteKey: String,
    val siteName: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val vodRemarks: String,
    val vodScore: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "config_repos")
data class RepoSourceEntity(
    @PrimaryKey
    val url: String,
    val name: String,
    val isActive: Boolean = false,
    val cachedJson: String = "",
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "poster_scrap_cache")
data class PosterScrapEntity(
    @PrimaryKey
    val cleanTitle: String,
    val releaseYear: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val ratingScore: String = "",
    val overview: String = "",
    val source: String = "tmdb",
    val updatedAt: Long = System.currentTimeMillis()
)
