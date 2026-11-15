package com.feringsapp.echobox.core.library

import android.content.Context
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * 内置转码器：直接调用打包进 APK 的 ffmpeg-kit-audio。
 * 只暴露音频编码器真实存在的预设，避免给用户一个必然失败的选项。
 */
class AudioTranscoder(private val context: Context) {

    data class Preset(
        val id: String,
        val label: String,
        val extension: String,
        /** ffmpeg 输出参数（不含输出路径）。 */
        val codecArgs: String,
    ) {
        companion object {
            val ALL = listOf(
                Preset("aac128", "标准 · AAC 128 kbps", "m4a", "-c:a aac -b:a 128k"),
                Preset("aac256", "高质量 · AAC 256 kbps", "m4a", "-c:a aac -b:a 256k"),
                Preset("flac", "无损 · FLAC", "flac", "-c:a flac -compression_level 5"),
                Preset("wav", "无损 · PCM 16 bit", "wav", "-c:a pcm_s16le"),
                Preset("opus", "小体积 · Opus 96 kbps", "opus", "-c:a libopus -b:a 96k"),
            )
        }
    }

    data class Result(val output: File, val durationMs: Long)

    private val storage = MediaStorage(context)

    suspend fun transcode(
        input: File,
        preset: Preset,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val outputDir = storage.transcodeDir()
        val baseName = input.nameWithoutExtension.ifBlank { "audio" }
        var output = File(outputDir, "$baseName.${preset.extension}")
        var index = 1
        while (output.exists()) {
            output = File(outputDir, "$baseName-$index.${preset.extension}")
            index++
        }
        val duration = durationMsOf(input)
        val command = "-y -hide_banner -i \"${input.absolutePath}\" -vn ${preset.codecArgs} \"${output.absolutePath}\""

        val session = runFfmpeg(command, duration) { onProgress(it) }
        val returnCode = session.returnCode
        if (!ReturnCode.isSuccess(returnCode) || !output.exists() || output.length() == 0L) {
            output.delete()
            error("转码失败：${session.failStackTrace?.take(300) ?: "ffmpeg 返回码 $returnCode"}")
        }
        Result(output, duration)
    }

    private suspend fun runFfmpeg(
        command: String,
        durationMs: Long,
        onProgress: (Float) -> Unit,
    ): FFmpegSession = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeAsync(
            command,
            { finished ->
                if (continuation.isActive) continuation.resume(finished)
            },
            null,
        ) { statistics ->
            if (durationMs > 0) {
                val ratio = (statistics.time / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
                onProgress(ratio)
            }
        }
        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    fun durationMsOf(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
}
