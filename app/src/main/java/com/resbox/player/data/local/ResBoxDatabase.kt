package com.resbox.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.resbox.player.data.local.dao.FavoriteVodDao
import com.resbox.player.data.local.dao.PosterScrapDao
import com.resbox.player.data.local.dao.RepoSourceDao
import com.resbox.player.data.local.dao.WatchHistoryDao
import com.resbox.player.data.local.entity.FavoriteVodEntity
import com.resbox.player.data.local.entity.PosterScrapEntity
import com.resbox.player.data.local.entity.RepoSourceEntity
import com.resbox.player.data.local.entity.WatchHistoryEntity

@Database(
    entities = [
        WatchHistoryEntity::class,
        FavoriteVodEntity::class,
        RepoSourceEntity::class,
        PosterScrapEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ResBoxDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteVodDao(): FavoriteVodDao
    abstract fun repoSourceDao(): RepoSourceDao
    abstract fun posterScrapDao(): PosterScrapDao
}
