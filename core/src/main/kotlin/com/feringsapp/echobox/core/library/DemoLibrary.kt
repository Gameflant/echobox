package com.feringsapp.echobox.core.library

import android.content.Context
import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.model.Authorization
import com.feringsapp.echobox.core.model.SeriesRef
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.core.sync.toEntity
import java.io.File

/**
 * 内置示例曲库：首次启动把 assets/demo 里的音频与歌词复制到 filesDir，并写入 Room。
 * 仓库接入后这批曲目仍保留，作为离线兜底。
 */
class DemoLibrary(
    private val context: Context,
    private val db: EchoDatabase,
) {
    private data class Demo(
        val id: String,
        val title: String,
        val asset: String,
        val durationSec: Int,
        val note: String,
        val index: Int,
    )

    private val demos = listOf(
        Demo("demo-snowdin", "Snowdin Breeze", "snowdin.wav", 10, "92 BPM", 1),
        Demo("demo-waterfall", "Waterfall Echo", "waterfall.wav", 13, "76 BPM", 2),
        Demo("demo-hotland", "Hotland Pulse", "hotland.wav", 8, "124 BPM", 3),
    )

    private fun dir(): File = File(context.filesDir, "demo").apply { mkdirs() }

    suspend fun ensureImported() {
        copyAssets()
        if (db.trackDao().count() > 0) return
        val tracks = demos.map { demo ->
            Track(
                id = demo.id,
                title = demo.title,
                author = "EchoBox",
                game = "Undertale",
                tags = listOf("示例", "钢琴"),
                series = SeriesRef("内置示例", demo.index, demos.size),
                authorization = Authorization.SELF_MADE,
                resourceLicense = "CC0",
                file = demo.asset,
                format = "wav",
                duration = demo.durationSec,
                cover = "",
                hash = "",
                uploadedAt = "2026-11-15T00:00:00Z",
            ).toEntity()
        }
        db.trackDao().upsertAll(tracks)
    }

    /** 音频和同名歌词都复制一份；已经存在的文件不覆盖，避免每次启动重写。 */
    private fun copyAssets() {
        val target = dir()
        val names = demos.flatMap { listOf(it.asset, it.asset.substringBeforeLast('.') + ".lrc") }
        names.forEach { name ->
            val file = File(target, name)
            if (file.exists()) return@forEach
            runCatching {
                context.assets.open("demo/$name").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { file.delete() }
        }
    }

    /** 把曲目里的文件名解析成可播放的本地 URI；不是内置曲目就原样返回。 */
    fun resolveUri(file: String): String {
        val local = File(dir(), file)
        return if (local.exists()) local.toURI().toString() else file
    }

    /** 同名 .lrc 存在就返回全文，否则返回空串（界面显示“暂无歌词”）。 */
    fun lyricsOf(file: String): String {
        if (file.isBlank()) return ""
        val lrc = File(dir(), file.substringBeforeLast('.') + ".lrc")
        if (!lrc.exists()) return ""
        return runCatching { lrc.readText() }.getOrDefault("")
    }

    fun demoFiles(): List<File> =
        demos.map { File(dir(), it.asset) }.filter { it.exists() }
}
