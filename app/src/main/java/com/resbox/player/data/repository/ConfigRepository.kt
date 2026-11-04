package com.resbox.player.data.repository

import com.resbox.player.data.local.dao.RepoSourceDao
import com.resbox.player.data.local.datastore.AppPreferences
import com.resbox.player.data.local.datastore.SettingsDataStore
import com.resbox.player.data.local.entity.RepoSourceEntity
import com.resbox.player.data.model.MultiRepoConfig
import com.resbox.player.data.model.TvBoxConfig
import com.resbox.player.data.remote.crypto.ConfigDecryptor
import com.resbox.player.data.remote.serializer.ResilientJson
import com.resbox.player.data.spider.BuiltinShowcaseData
import com.resbox.player.data.spider.JarLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages TVBox Single-Line and Multi-Repository (`urls` / `storeHouse`) configurations.
 * Features:
 * - Automatic decryption via `ConfigDecryptor` (Base64, AES-CBC `2423`, AES-ECB, PNG/BMP `**` steganography).
 * - Automatic multi-repo expansion into `RepoSourceDao`.
 * - Offline caching in Room DB and automatic failover to backup repositories if the primary repo fails.
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val repoSourceDao: RepoSourceDao,
    private val settingsDataStore: SettingsDataStore,
    private val jarLoader: JarLoader
) {
    private val _activeConfigState = MutableStateFlow(BuiltinShowcaseData.defaultConfig)
    val activeConfigState: StateFlow<TvBoxConfig> = _activeConfigState.asStateFlow()

    val savedReposFlow: Flow<List<RepoSourceEntity>> = repoSourceDao.observeAllRepos()

    suspend fun initializeConfig() = withContext(Dispatchers.IO) {
        val prefs = settingsDataStore.preferencesFlow.first()
        val existingRepos = repoSourceDao.getAllRepos()
        if (existingRepos.isEmpty()) {
            val defaultRepo = RepoSourceEntity(
                url = AppPreferences.DEFAULT_BUILTIN_REPO_URL,
                name = "ResBox 官方 4K 臻彩仓 (内置离线可用)",
                isActive = true,
                cachedJson = ResilientJson.encodeToString(
                    TvBoxConfig.serializer(),
                    BuiltinShowcaseData.defaultConfig
                )
            )
            repoSourceDao.upsertRepo(defaultRepo)
        }
        loadAndActivateConfigUrl(prefs.activeRepoUrl)
    }

    /**
     * Loads either a single-line TVBox config URL or a Multi-Repo (`urls` / `storeHouse`) URL.
     * On network failure, transparently falls back to cached JSON or alternate stored repositories.
     */
    suspend fun loadAndActivateConfigUrl(
        configUrl: String,
        customName: String = "自定义源仓库"
    ): Result<TvBoxConfig> = withContext(Dispatchers.IO) {
        // Release previous ClassLoader / Spider instances before switching config (Coding Rule #2)
        jarLoader.clearAllSpiders()

        if (configUrl == AppPreferences.DEFAULT_BUILTIN_REPO_URL || configUrl.isBlank()) {
            val builtin = BuiltinShowcaseData.defaultConfig
            _activeConfigState.value = builtin
            settingsDataStore.updateActiveRepoUrl(AppPreferences.DEFAULT_BUILTIN_REPO_URL)
            repoSourceDao.setActiveRepoUrl(AppPreferences.DEFAULT_BUILTIN_REPO_URL)
            return@withContext Result.success(builtin)
        }

        val fetchedText = fetchAndDecryptRemoteText(configUrl)
        if (fetchedText != null) {
            val parsedResult = parseSingleOrMultiRepo(configUrl, customName, fetchedText)
            if (parsedResult != null) {
                _activeConfigState.value = parsedResult
                return@withContext Result.success(parsedResult)
            }
        }

        // Offline / Failure Fallback 1: Check local Room cache for this URL
        val cachedRepos = repoSourceDao.getAllRepos()
        val matchedCache = cachedRepos.find { it.url == configUrl && it.cachedJson.isNotBlank() }
        if (matchedCache != null) {
            runCatching {
                ResilientJson.decodeFromString(TvBoxConfig.serializer(), matchedCache.cachedJson)
            }.getOrNull()?.let { cachedConfig ->
                _activeConfigState.value = cachedConfig
                if (cachedConfig.spider.isNotBlank()) {
                    jarLoader.loadMainJar(cachedConfig.spider)
                }
                return@withContext Result.success(cachedConfig)
            }
        }

        // Offline / Failure Fallback 2: Automatically switch to next available backup repository
        for (backup in cachedRepos) {
            if (backup.url != configUrl && backup.cachedJson.isNotBlank()) {
                val backupConfig = runCatching {
                    ResilientJson.decodeFromString(TvBoxConfig.serializer(), backup.cachedJson)
                }.getOrNull()
                if (backupConfig != null && backupConfig.sites.isNotEmpty()) {
                    _activeConfigState.value = backupConfig
                    settingsDataStore.updateActiveRepoUrl(backup.url)
                    repoSourceDao.setActiveRepoUrl(backup.url)
                    return@withContext Result.success(backupConfig)
                }
            }
        }

        // Final guaranteed fallback: Built-in showcase config
        _activeConfigState.value = BuiltinShowcaseData.defaultConfig
        Result.success(BuiltinShowcaseData.defaultConfig)
    }

    private suspend fun parseSingleOrMultiRepo(
        sourceUrl: String,
        sourceName: String,
        cleanJson: String
    ): TvBoxConfig? {
        // Check if this is a Multi-Repo index (`urls` or `storeHouse`)
        if (cleanJson.contains("\"urls\"") || cleanJson.contains("\"storeHouse\"")) {
            val multiRepo = runCatching {
                ResilientJson.decodeFromString(MultiRepoConfig.serializer(), cleanJson)
            }.getOrNull()

            val entries = multiRepo?.allEntries.orEmpty()
            if (entries.isNotEmpty()) {
                val entities = entries.mapIndexed { idx, entry ->
                    RepoSourceEntity(
                        url = entry.url,
                        name = entry.name.ifBlank { "分仓线路 #${idx + 1}" },
                        isActive = idx == 0
                    )
                }
                repoSourceDao.upsertRepos(entities)

                // Try activating the first working sub-repository in the multi-repo list
                for (entry in entries) {
                    val subJson = fetchAndDecryptRemoteText(entry.url) ?: continue
                    val subConfig = runCatching {
                        ResilientJson.decodeFromString(TvBoxConfig.serializer(), subJson)
                    }.getOrNull()
                    if (subConfig != null && subConfig.sites.isNotEmpty()) {
                        repoSourceDao.upsertRepo(
                            RepoSourceEntity(
                                url = entry.url,
                                name = entry.name,
                                isActive = true,
                                cachedJson = subJson
                            )
                        )
                        repoSourceDao.setActiveRepoUrl(entry.url)
                        settingsDataStore.updateActiveRepoUrl(entry.url)
                        if (subConfig.spider.isNotBlank()) {
                            jarLoader.loadMainJar(subConfig.spider)
                        }
                        return subConfig
                    }
                }
            }
        }

        // Standard single-line TVBox config
        val singleConfig = runCatching {
            ResilientJson.decodeFromString(TvBoxConfig.serializer(), cleanJson)
        }.getOrNull()

        if (singleConfig != null && singleConfig.sites.isNotEmpty()) {
            repoSourceDao.upsertRepo(
                RepoSourceEntity(
                    url = sourceUrl,
                    name = sourceName,
                    isActive = true,
                    cachedJson = cleanJson
                )
            )
            repoSourceDao.setActiveRepoUrl(sourceUrl)
            settingsDataStore.updateActiveRepoUrl(sourceUrl)
            if (singleConfig.spider.isNotBlank()) {
                jarLoader.loadMainJar(singleConfig.spider)
            }
            return singleConfig
        }
        return null
    }

    private fun fetchAndDecryptRemoteText(url: String): String? {
        if (!url.startsWith("http")) return null
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "okhttp/4.12.0")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val rawBytes = response.body?.bytes() ?: return null
                ConfigDecryptor.decryptAndCleanConfig(rawBytes)
            }
        } catch (_: Exception) {
            null
        }
    }
}
