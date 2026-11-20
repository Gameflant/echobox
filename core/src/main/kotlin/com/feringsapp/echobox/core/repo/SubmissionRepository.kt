package com.feringsapp.echobox.core.repo

import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.db.SubmissionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** 投稿草稿：字段与 tracks.json 的 schema 对齐，提交前先落库，避免填一半丢失。 */
data class SubmissionDraft(
    val title: String,
    val author: String,
    val game: String = "Undertale",
    val tags: List<String> = emptyList(),
    val series: String = "",
    val authorization: String = "self_made",
    val resourceLicense: String = "CC0",
    val audioName: String = "",
    val coverName: String = "",
    val durationSec: Int = 0,
    val sha256: String = "",
)

class SubmissionRepository(private val db: EchoDatabase) {

    fun observeAll(): Flow<List<SubmissionEntity>> = db.submissionDao().observeAll()

    suspend fun saveDraft(draft: SubmissionDraft): String {
        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("id", id)
            put("title", draft.title)
            put("author", draft.author)
            put("game", draft.game)
            put("series", draft.series)
            put("authorization", draft.authorization)
            put("resource_license", draft.resourceLicense)
            put("file", draft.audioName)
            put("cover", draft.coverName)
            put("duration", draft.durationSec)
            put("hash", if (draft.sha256.isBlank()) "" else "sha256:${draft.sha256}")
            put(
                "tags",
                buildJsonArray { draft.tags.forEach { add(it) } },
            )
        }
        val now = System.currentTimeMillis()
        db.submissionDao().upsert(
            SubmissionEntity(
                recordId = id,
                state = "DRAFT",
                reason = null,
                submittedAtEpoch = now,
                updatedAtEpoch = now,
                payloadJson = payload.toString(),
                audioName = draft.audioName.ifBlank { null },
                coverName = draft.coverName.ifBlank { null },
            ),
        )
        return id
    }

    suspend fun delete(recordId: String) = db.submissionDao().delete(recordId)

    /**
     * 用服务端 pending 目录校正本地状态：
     * 本地还是 PENDING、但服务端已经不在待审队列里的，视为已处理。
     */
    suspend fun syncStates(pendingIds: Set<String>) {
        val now = System.currentTimeMillis()
        db.submissionDao().observeAll().first()
            .filter { it.state == "PENDING" && it.recordId !in pendingIds }
            .forEach { entity ->
                db.submissionDao().upsert(entity.copy(state = "PROCESSED", updatedAtEpoch = now))
            }
    }
}
