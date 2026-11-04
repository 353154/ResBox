package com.github.catvod.crawler

import android.content.Context
import okhttp3.Dns

/**
 * 100% binary-compatible abstract base class for TVBox / CatVod Spider plugins (`csp_*`).
 * Dynamic `.jar` / `.dex` plugins loaded via `DexClassLoader` extend `com.github.catvod.crawler.Spider`.
 */
abstract class Spider {

    open fun init(context: Context) {}

    open fun init(context: Context, extend: String) {
        init(context)
    }

    open fun homeContent(filter: Boolean): String = "{}"

    open fun homeVideoContent(): String = "{}"

    open fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean,
        extend: HashMap<String, String>
    ): String = "{}"

    open fun detailContent(ids: List<String>): String = "{}"

    open fun searchContent(key: String, quick: Boolean): String = "{}"

    open fun searchContent(key: String, quick: Boolean, pg: String): String {
        return searchContent(key, quick)
    }

    open fun playerContent(flag: String, id: String, vipFlags: List<String>): String = "{}"

    open fun manualVideoCheck(): Boolean = false

    open fun isVideoFormat(url: String): Boolean = false

    open fun localProxy(param: Map<String, String>): Array<Any>? = null

    open fun destroy() {}

    companion object {
        @JvmStatic
        fun safeDns(): Dns = Dns.SYSTEM
    }
}
