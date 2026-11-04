package com.github.catvod.crawler

import android.util.Log

/**
 * Standard logging utility invoked by third-party CatVod Spider JARs.
 */
object SpiderDebug {
    private const val TAG = "ResBoxSpider"

    @JvmStatic
    fun log(msg: String?) {
        if (!msg.isNullOrBlank()) {
            Log.d(TAG, msg)
        }
    }

    @JvmStatic
    fun log(th: Throwable?) {
        if (th != null) {
            Log.e(TAG, th.message ?: "Spider error", th)
        }
    }
}
