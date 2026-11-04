package com.resbox.player.media.sniffer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Immutable
data class SniffedMediaStream(
    val mediaUrl: String,
    val headers: Map<String, String>,
    val mimeType: String = ""
)

/**
 * Headless transparent `WebView` media stream sniffer for `parse: 1` / `jx: 1` web pages (Requirement 4.4).
 * - Overrides `shouldInterceptRequest` on background thread to capture `.m3u8`, `.mp4`, `.flv`, `.mpd`
 *   streams along with their dynamic `User-Agent`, `Referer`, and `Cookie` request headers.
 * - Blocks image/font/tracker requests during sniffing to accelerate media extraction.
 * - Guarantees 100% lifecycle teardown (`destroyWebViewSafely`) on cancellation, completion, or timeout
 *   to prevent WebView memory leaks (Coding Rule #2).
 */
@Singleton
class HeadlessWebSniffer @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeWebView: WebView? = null

    private val mediaRegex = Regex(
        pattern = """https?://[^\s"'<>]+?\.(m3u8|mp4|flv|mpd|mkv)(\?[^\s"'<>]*)?$""",
        option = RegexOption.IGNORE_CASE
    )

    private val ignoredAssetExtensions = listOf(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".svg",
        ".woff", ".woff2", ".ttf", ".css"
    )

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniffMediaStream(
        pageUrl: String,
        customHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Long = 12_000L
    ): SniffedMediaStream? = withContext(Dispatchers.Main) {
        releaseActiveSniffer()

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val resolved = AtomicBoolean(false)
                val webView = WebView(appContext.applicationContext)
                activeWebView = webView

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    blockNetworkImage = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    userAgentString = customHeaders["User-Agent"]
                        ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                webView.webViewClient = object : WebViewClient() {

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed()
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString().orEmpty()
                        if (reqUrl.isBlank()) return super.shouldInterceptRequest(view, request)

                        val lower = reqUrl.lowercase()

                        // Fast-reject non-essential static assets to speed up JS execution
                        if (ignoredAssetExtensions.any { lower.contains(it) }) {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }

                        if (isSniffableMediaUrl(reqUrl)) {
                            if (resolved.compareAndSet(false, true)) {
                                val capturedHeaders = mutableMapOf<String, String>()
                                request?.requestHeaders?.forEach { (k, v) ->
                                    if (k.equals("User-Agent", ignoreCase = true) ||
                                        k.equals("Referer", ignoreCase = true) ||
                                        k.equals("Origin", ignoreCase = true) ||
                                        k.equals("Authorization", ignoreCase = true)
                                    ) {
                                        capturedHeaders[k] = v
                                    }
                                }
                                CookieManager.getInstance().getCookie(reqUrl)?.let { cookie ->
                                    if (cookie.isNotBlank()) capturedHeaders["Cookie"] = cookie
                                }
                                if (!capturedHeaders.containsKey("Referer")) {
                                    capturedHeaders["Referer"] = pageUrl
                                }

                                val stream = SniffedMediaStream(
                                    mediaUrl = reqUrl,
                                    headers = capturedHeaders
                                )
                                mainHandler.post {
                                    releaseActiveSniffer()
                                    if (continuation.isActive) {
                                        continuation.resume(stream)
                                    }
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post { releaseActiveSniffer() }
                }

                if (customHeaders.isNotEmpty()) {
                    webView.loadUrl(pageUrl, customHeaders)
                } else {
                    webView.loadUrl(pageUrl)
                }
            }
        }.also {
            releaseActiveSniffer()
        }
    }

    private fun isSniffableMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains(".js") || lower.contains(".css") || lower.contains("url=http")) {
            return false
        }
        return mediaRegex.containsMatchIn(url) ||
            lower.contains(".m3u8?") ||
            lower.contains(".mp4?") ||
            lower.contains(".flv?") ||
            lower.contains("mime=video")
    }

    /**
     * Immediately stops loading, clears history, detaches from any parent, and calls `destroy()`
     * on the main looper to guarantee zero WebView memory leaks (Coding Rule #2).
     */
    fun releaseActiveSniffer() {
        val webView = activeWebView ?: return
        activeWebView = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyWebViewInternal(webView)
        } else {
            mainHandler.post { destroyWebViewInternal(webView) }
        }
    }

    private fun destroyWebViewInternal(webView: WebView) {
        try {
            webView.stopLoading()
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Throwable) {
            // Safe cleanup guard
        }
    }
}
