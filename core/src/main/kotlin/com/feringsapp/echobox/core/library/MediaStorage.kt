package com.feringsapp.echobox.core.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 本地占用统计与清理：示例曲库、转码输出、应用缓存三块。 */
class MediaStorage(private val context: Context) {

    data class Usage(
        val demoBytes: Long,
        val transcodeBytes: Long,
        val cacheBytes: Long,
    ) {
        val totalBytes: Long get() = demoBytes + transcodeBytes + cacheBytes
    }

    fun demoDir(): File = File(context.filesDir, "demo")

    fun transcodeDir(): File = File(context.filesDir, "transcode").apply { mkdirs() }

    suspend fun usage(): Usage = withContext(Dispatchers.IO) {
        Usage(
            demoBytes = sizeOf(demoDir()),
            transcodeBytes = sizeOf(transcodeDir()),
            cacheBytes = sizeOf(context.cacheDir),
        )
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val freed = sizeOf(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        freed
    }

    suspend fun clearTranscode(): Long = withContext(Dispatchers.IO) {
        val dir = transcodeDir()
        val freed = sizeOf(dir)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        freed
    }

    fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        val children = file.listFiles() ?: return 0L
        var total = 0L
        for (child in children) total += sizeOf(child)
        return total
    }

    companion object {
        fun format(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
            bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
            else -> "$bytes B"
        }
    }
}
