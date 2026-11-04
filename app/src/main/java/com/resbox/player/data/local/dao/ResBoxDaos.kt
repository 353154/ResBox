package com.resbox.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resbox.player.data.local.entity.FavoriteVodEntity
import com.resbox.player.data.local.entity.PosterScrapEntity
import com.resbox.player.data.local.entity.RepoSourceEntity
import com.resbox.player.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT 100")
    fun observeRecentHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE compositeId = :compositeId LIMIT 1")
    suspend fun getHistoryById(compositeId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE vodName = :vodName ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findHistoryByVodName(vodName: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE compositeId = :compositeId")
    suspend fun deleteHistory(compositeId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}

@Dao
interface FavoriteVodDao {
    @Query("SELECT * FROM vod_favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteVodEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM vod_favorites WHERE compositeId = :compositeId)")
    fun observeIsFavorite(compositeId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteVodEntity)

    @Query("DELETE FROM vod_favorites WHERE compositeId = :compositeId")
    suspend fun removeFavorite(compositeId: String)
}

@Dao
interface RepoSourceDao {
    @Query("SELECT * FROM config_repos ORDER BY isActive DESC, lastSyncedAt DESC")
    fun observeAllRepos(): Flow<List<RepoSourceEntity>>

    @Query("SELECT * FROM config_repos ORDER BY isActive DESC, lastSyncedAt DESC")
    suspend fun getAllRepos(): List<RepoSourceEntity>

    @Query("SELECT * FROM config_repos WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRepo(): RepoSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepo(repo: RepoSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepos(repos: List<RepoSourceEntity>)

    @Query("UPDATE config_repos SET isActive = CASE WHEN url = :activeUrl THEN 1 ELSE 0 END")
    suspend fun setActiveRepoUrl(activeUrl: String)

    @Query("DELETE FROM config_repos WHERE url = :url")
    suspend fun deleteRepo(url: String)
}

@Dao
interface PosterScrapDao {
    @Query("SELECT * FROM poster_scrap_cache WHERE cleanTitle = :cleanTitle LIMIT 1")
    suspend fun getScrapByTitle(cleanTitle: String): PosterScrapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScrap(entity: PosterScrapEntity)

    @Query("DELETE FROM poster_scrap_cache WHERE updatedAt < :expireTimestamp")
    suspend fun cleanOldScraps(expireTimestamp: Long)
}
