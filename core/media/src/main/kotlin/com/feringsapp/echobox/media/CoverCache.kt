package com.feringsapp.echobox.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 通知大图用的封面缓存。
 * 通知在主线程构建，不能阻塞下载，所以先返回缓存，同时后台预取，下一次更新就能带上。
 */
object CoverCache {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "cover-cache").apply { isDaemon = true }
    }

    private val loading: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun get(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        cache.get(url)?.let { return it }
        prefetch(url)
        return null
    }

    fun prefetch(url: String?) {
        if (url.isNullOrBlank() || !url.startsWith("https://")) return
        if (cache.get(url) != null) return
        if (!loading.add(url)) return
        executor.execute {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { cache.put(url, it) }
            loading.remove(url)
        }
    }
}
