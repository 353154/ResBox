package com.resbox.player.data.spider

import android.content.Context
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamically downloads, caches, verifies (MD5), and loads remote TVBox `.jar` / `.dex` Spider plugins
 * using `DexClassLoader`. Enforces strict lifecycle cleanup (`clearAllSpiders`) when switching repositories
 * to prevent ClassLoader memory leaks (Coding Rule #2).
 */
@Singleton
class JarLoader @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient
) {
    private val mutex = Mutex()
    private val classLoaders = ConcurrentHashMap<String, DexClassLoader>()
    private val spiderInstances = ConcurrentHashMap<String, Spider>()
    private var defaultJarKey: String = ""

    private val jarCacheDir: File by lazy {
        File(appContext.codeCacheDir, "resbox_spiders").apply { mkdirs() }
    }

    private val optimizedDexDir: File by lazy {
        File(appContext.codeCacheDir, "resbox_opt_dex").apply { mkdirs() }
    }

    /**
     * Preloads the root `spider` JAR specified in `TvBoxConfig.spider`.
     * Supports TVBox format `"http://.../custom_spider.jar;md5;7a8b9c..."`.
     */
    suspend fun loadMainJar(jarSpec: String): Boolean = withContext(Dispatchers.IO) {
        if (jarSpec.isBlank()) return@withContext false
        mutex.withLock {
            val (jarUrl, expectedMd5) = parseJarSpec(jarSpec)
            val key = md5Hex(jarUrl)
            defaultJarKey = key
            ensureClassLoaderLocked(key, jarUrl, expectedMd5) != null
        }
    }

    /**
     * Instantiates or retrieves a cached `Spider` for a given `SiteBean`.
     */
    suspend fun getSpider(
        siteKey: String,
        apiName: String,
        ext: String,
        customJarSpec: String
    ): Spider? = withContext(Dispatchers.IO) {
        val instanceCacheKey = "${siteKey}_${apiName}"
        spiderInstances[instanceCacheKey]?.let { return@withContext it }

        mutex.withLock {
            spiderInstances[instanceCacheKey]?.let { return@withLock it }

            val jarKey = if (customJarSpec.isNotBlank()) {
                val (url, expectedMd5) = parseJarSpec(customJarSpec)
                val k = md5Hex(url)
                ensureClassLoaderLocked(k, url, expectedMd5)
                k
            } else {
                defaultJarKey
            }

            val loader = classLoaders[jarKey] ?: return@withLock null
            val cleanClassName = apiName.removePrefix("csp_").trim()
            val candidateClasses = listOf(
                "com.github.catvod.spider.$cleanClassName",
                "com.github.catvod.crawler.$cleanClassName",
                cleanClassName
            )

            for (className in candidateClasses) {
                try {
                    val clazz = loader.loadClass(className)
                    if (Spider::class.java.isAssignableFrom(clazz)) {
                        val spider = clazz.getDeclaredConstructor().newInstance() as Spider
                        spider.init(appContext, ext)
                        spiderInstances[instanceCacheKey] = spider
                        return@withLock spider
                    }
                } catch (_: ClassNotFoundException) {
                    // Try next candidate package
                } catch (t: Throwable) {
                    SpiderDebug.log(t)
                }
            }
            null
        }
    }

    private fun ensureClassLoaderLocked(
        key: String,
        jarUrl: String,
        expectedMd5: String
    ): DexClassLoader? {
        classLoaders[key]?.let { return it }

        val targetJarFile = File(jarCacheDir, "$key.jar")
        val needsDownload = !targetJarFile.exists() ||
            targetJarFile.length() == 0L ||
            (expectedMd5.isNotBlank() && !fileMd5Matches(targetJarFile, expectedMd5))

        if (needsDownload) {
            val downloaded = downloadJarFile(jarUrl, targetJarFile)
            if (!downloaded) return null
        }

        // Make read-only as required by Android 14+ (API 34+) dynamic code loading security policy
        targetJarFile.setReadOnly()

        return try {
            val dexLoader = DexClassLoader(
                targetJarFile.absolutePath,
                optimizedDexDir.absolutePath,
                null,
                appContext.classLoader
            )
            classLoaders[key] = dexLoader
            dexLoader
        } catch (t: Throwable) {
            SpiderDebug.log(t)
            null
        }
    }

    private fun downloadJarFile(url: String, destination: File): Boolean {
        if (!url.startsWith("http")) return false
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) okhttp/4.12.0")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val bytes = response.body?.bytes() ?: return false
                // Handle image-steganography JARs where a PNG header precedes the ZIP/JAR magic bytes (PK)
                val pkOffset = findZipMagicOffset(bytes)
                val cleanJarBytes = if (pkOffset > 0) {
                    bytes.copyOfRange(pkOffset, bytes.size)
                } else {
                    bytes
                }
                destination.setWritable(true)
                destination.writeBytes(cleanJarBytes)
                true
            }
        } catch (t: Throwable) {
            SpiderDebug.log(t)
            false
        }
    }

    /**
     * Locates the ZIP/JAR `PK` signature inside steganographic image files.
     */
    private fun findZipMagicOffset(bytes: ByteArray): Int {
        for (i in 0 until (bytes.size - 4)) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() &&
                bytes[i + 3] == 0x04.toByte()
            ) {
                return i
            }
        }
        return 0
    }

    /**
     * Invokes `destroy()` on all loaded Spider instances and releases all ClassLoader references
     * to prevent memory leaks when switching repositories (Coding Rule #2).
     */
    suspend fun clearAllSpiders() = withContext(Dispatchers.IO) {
        mutex.withLock {
            spiderInstances.values.forEach { spider ->
                try {
                    spider.destroy()
                } catch (t: Throwable) {
                    SpiderDebug.log(t)
                }
            }
            spiderInstances.clear()
            classLoaders.clear()
            defaultJarKey = ""
        }
    }

    private fun parseJarSpec(spec: String): Pair<String, String> {
        val parts = spec.split(";").map { it.trim() }
        val url = parts.firstOrNull().orEmpty()
        val md5Index = parts.indexOfFirst { it.equals("md5", ignoreCase = true) }
        val md5 = if (md5Index >= 0 && md5Index + 1 < parts.size) {
            parts[md5Index + 1]
        } else {
            ""
        }
        return url to md5
    }

    private fun fileMd5Matches(file: File, expectedMd5: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(file.readBytes())
            val hex = hashBytes.joinToString("") { "%02x".format(it) }
            hex.equals(expectedMd5.trim(), ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
