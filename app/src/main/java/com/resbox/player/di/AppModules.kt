package com.resbox.player.di

import android.content.Context
import androidx.room.Room
import com.resbox.player.data.local.ResBoxDatabase
import com.resbox.player.data.local.dao.FavoriteVodDao
import com.resbox.player.data.local.dao.PosterScrapDao
import com.resbox.player.data.local.dao.RepoSourceDao
import com.resbox.player.data.local.dao.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object AppModules {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Many third-party TVBox CMS / Spider endpoints use self-signed or expired SSL certificates.
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ResBoxDatabase {
        return Room.databaseBuilder(
            context,
            ResBoxDatabase::class.java,
            "resbox_database.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideWatchHistoryDao(db: ResBoxDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideFavoriteVodDao(db: ResBoxDatabase): FavoriteVodDao = db.favoriteVodDao()

    @Provides
    fun provideRepoSourceDao(db: ResBoxDatabase): RepoSourceDao = db.repoSourceDao()

    @Provides
    fun providePosterScrapDao(db: ResBoxDatabase): PosterScrapDao = db.posterScrapDao()
}
