package com.resbox.player.data.local.datastore

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.resBoxDataStore: DataStore<Preferences> by preferencesDataStore(name = "resbox_settings")

@Immutable
data class AppPreferences(
    val activeRepoUrl: String = DEFAULT_BUILTIN_REPO_URL,
    val activeHomeSiteKey: String = "",
    val enableDynamicColor: Boolean = true,
    val enableHlsAdBlock: Boolean = true,
    val enableDanmaku: Boolean = true,
    val enablePosterScraping: Boolean = true,
    val tmdbApiKey: String = "",
    val concurrentSearchLimit: Int = 10,
    val defaultSkipIntroMs: Long = 0L,
    val defaultSkipOutroMs: Long = 0L,
    val forceTvMode: Boolean = false
) {
    companion object {
        const val DEFAULT_BUILTIN_REPO_URL = "resbox://builtin/default_config.json"
        const val DEFAULT_TMDB_API_KEY = "388bb14c00f7e447b9687e812543e488" // Built-in default key for instant scraping
    }
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACTIVE_REPO_URL = stringPreferencesKey("active_repo_url")
        val ACTIVE_HOME_SITE_KEY = stringPreferencesKey("active_home_site_key")
        val ENABLE_DYNAMIC_COLOR = booleanPreferencesKey("enable_dynamic_color")
        val ENABLE_HLS_AD_BLOCK = booleanPreferencesKey("enable_hls_ad_block")
        val ENABLE_DANMAKU = booleanPreferencesKey("enable_danmaku")
        val ENABLE_POSTER_SCRAPING = booleanPreferencesKey("enable_poster_scraping")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val CONCURRENT_SEARCH_LIMIT = intPreferencesKey("concurrent_search_limit")
        val DEFAULT_SKIP_INTRO_MS = longPreferencesKey("default_skip_intro_ms")
        val DEFAULT_SKIP_OUTRO_MS = longPreferencesKey("default_skip_outro_ms")
        val FORCE_TV_MODE = booleanPreferencesKey("force_tv_mode")
    }

    val preferencesFlow: Flow<AppPreferences> = context.resBoxDataStore.data.map { prefs ->
        AppPreferences(
            activeRepoUrl = prefs[Keys.ACTIVE_REPO_URL] ?: AppPreferences.DEFAULT_BUILTIN_REPO_URL,
            activeHomeSiteKey = prefs[Keys.ACTIVE_HOME_SITE_KEY] ?: "",
            enableDynamicColor = prefs[Keys.ENABLE_DYNAMIC_COLOR] ?: true,
            enableHlsAdBlock = prefs[Keys.ENABLE_HLS_AD_BLOCK] ?: true,
            enableDanmaku = prefs[Keys.ENABLE_DANMAKU] ?: true,
            enablePosterScraping = prefs[Keys.ENABLE_POSTER_SCRAPING] ?: true,
            tmdbApiKey = prefs[Keys.TMDB_API_KEY] ?: "",
            concurrentSearchLimit = (prefs[Keys.CONCURRENT_SEARCH_LIMIT] ?: 10).coerceIn(4, 16),
            defaultSkipIntroMs = prefs[Keys.DEFAULT_SKIP_INTRO_MS] ?: 0L,
            defaultSkipOutroMs = prefs[Keys.DEFAULT_SKIP_OUTRO_MS] ?: 0L,
            forceTvMode = prefs[Keys.FORCE_TV_MODE] ?: false
        )
    }

    suspend fun updateActiveRepoUrl(url: String) {
        context.resBoxDataStore.edit { it[Keys.ACTIVE_REPO_URL] = url }
    }

    suspend fun updateActiveHomeSiteKey(siteKey: String) {
        context.resBoxDataStore.edit { it[Keys.ACTIVE_HOME_SITE_KEY] = siteKey }
    }

    suspend fun setEnableDynamicColor(enabled: Boolean) {
        context.resBoxDataStore.edit { it[Keys.ENABLE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setEnableHlsAdBlock(enabled: Boolean) {
        context.resBoxDataStore.edit { it[Keys.ENABLE_HLS_AD_BLOCK] = enabled }
    }

    suspend fun setEnableDanmaku(enabled: Boolean) {
        context.resBoxDataStore.edit { it[Keys.ENABLE_DANMAKU] = enabled }
    }

    suspend fun setEnablePosterScraping(enabled: Boolean) {
        context.resBoxDataStore.edit { it[Keys.ENABLE_POSTER_SCRAPING] = enabled }
    }

    suspend fun setTmdbApiKey(apiKey: String) {
        context.resBoxDataStore.edit { it[Keys.TMDB_API_KEY] = apiKey.trim() }
    }

    suspend fun setForceTvMode(forceTv: Boolean) {
        context.resBoxDataStore.edit { it[Keys.FORCE_TV_MODE] = forceTv }
    }

    suspend fun setDefaultSkipTimes(introMs: Long, outroMs: Long) {
        context.resBoxDataStore.edit {
            it[Keys.DEFAULT_SKIP_INTRO_MS] = introMs
            it[Keys.DEFAULT_SKIP_OUTRO_MS] = outroMs
        }
    }
}
