package com.feringsapp.echobox.core.repo

import android.content.Context
import android.util.Log
import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.network.GitHubApi
import com.feringsapp.echobox.core.settings.SubmitterId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/** 专辑选择结果：不加入 / 新建 / 已有。 */
enum class AlbumIntent { NONE, NEW, EXISTING }

data class UploadRequest(
    val title: String,
    val author: String,
    val game: String,
    val tags: List<String>,
    val albumIntent: AlbumIntent,
    val albumName: String,
    val authorization: String,
    val resourceLicense: String,
    val audioFile: File,
    val coverFile: File?,
    val durationSec: Int,
    val sha256: String,
)

/**
 * 真实投稿：音频与封面传到 staging 仓库的 release，元数据写成 pending/<id>.json。
 * App 不碰生产仓库，也不做删除；审核与合并由 Admin 处理。
 */
class SubmissionUploader(
    private val context: Context,
    private val api: GitHubApi,
    private val db: EchoDatabase,
    private val tag: String = "staging-inbox",
) {

    data class Progress(val stage: String, val bytes: Long, val total: Long, val percent: Int)

    suspend fun upload(
        request: UploadRequest,
        onProgress: (Progress) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val recordId = UUID.randomUUID().toString()

            // 同一份文件（sha256 相同）已经提交过就不重复传
            if (request.sha256.isNotBlank()) {
                val already = db.submissionDao().observeAll().first().any { entity ->
                    entity.state != "DRAFT" && entity.payloadJson.contains(request.sha256)
                }
                if (already) error("这首歌已经提交过，正在审核中")
            }

            onProgress(Progress("准备上传", 0, 0, 0))

            val release = api.releaseByTag(tag).getOrElse { error("staging 仓库没有 $tag release") }

            val audioExt = request.audioFile.extension.ifBlank { "bin" }
            val audioAsset = "audio-$recordId.$audioExt"
            val audioUrl = api.uploadAsset(
                release, audioAsset, request.audioFile, contentType(audioExt),
            ) { done, total ->
                val pct = if (total > 0) (done * 55 / total).toInt() else 0
                onProgress(Progress("上传音频", done, total, pct))
            }.getOrThrow()

            var coverAsset: String? = null
            var coverUrl: String? = null
            request.coverFile?.let { cover ->
                val ext = cover.extension.ifBlank { "png" }
                coverAsset = "cover-$recordId.$ext"
                coverUrl = api.uploadAsset(
                    release, coverAsset, cover, contentType(ext),
                ) { done, total ->
                    val pct = if (total > 0) (55 + done * 30 / total).toInt() else 55
                    onProgress(Progress("上传封面", done, total, pct))
                }.getOrThrow()
            }

            onProgress(Progress("写入投稿记录", 0, 0, 90))

            val now = System.currentTimeMillis()
            val submitterId = SubmitterId.get(context)
            val payload = buildJsonObject {
                put("id", recordId)
                put("submitter", submitterId)
                put("title", request.title)
                put("author", request.author)
                put("game", request.game)
                put("authorization", request.authorization)
                put("resource_license", request.resourceLicense)
                put("duration", request.durationSec)
                put("hash", if (request.sha256.isBlank()) "" else "sha256:${request.sha256}")
                put("submitted_at", now)
                put("audio_asset", audioAsset)
                put("audio_url", audioUrl)
                if (coverAsset != null) {
                    put("cover_asset", coverAsset)
                    put("cover_url", coverUrl)
                }
                put("album_intent", buildJsonObject {
                    put("mode", when (request.albumIntent) {
                        AlbumIntent.NONE -> "none"
                        AlbumIntent.NEW -> "new"
                        AlbumIntent.EXISTING -> "existing"
                    })
                    put("name", request.albumName)
                })
                put("tags", buildJsonArray { request.tags.forEach { add(it) } })
            }

            api.putFile(
                path = "pending/$recordId.json",
                bytes = payload.toString().toByteArray(),
                message = "submission: ${request.title}",
            ).getOrThrow()

            // 本地留档，状态从草稿变为待审核
            db.submissionDao().upsert(
                com.feringsapp.echobox.core.db.SubmissionEntity(
                    recordId = recordId,
                    state = "PENDING",
                    reason = null,
                    submittedAtEpoch = now,
                    updatedAtEpoch = now,
                    payloadJson = payload.toString(),
                    audioName = request.audioFile.name,
                    coverName = request.coverFile?.name,
                ),
            )
            onProgress(Progress("已完成", 0, 0, 100))
            recordId
        }.onFailure { Log.w("EchoBox", "投稿失败", it) }
    }

    /** 清理上次选完文件后留下的临时文件。 */
    fun cleanupTemp(files: List<File>) {
        files.forEach { runCatching { if (it.exists()) it.delete() } }
    }

    private fun contentType(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    fun cacheAudioDir(): File = File(context.cacheDir, "upload").apply { mkdirs() }
}
