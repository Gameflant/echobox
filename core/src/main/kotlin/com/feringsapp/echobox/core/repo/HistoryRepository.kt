package com.feringsapp.echobox.core.repo

import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.db.PlayHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 播放历史：只记 trackId + 时间，「最近播放」再和曲库 join。 */
class HistoryRepository(private val db: EchoDatabase) {

    fun observeRecent(limit: Int = 40): Flow<List<PlayHistoryEntity>> =
        db.historyDao().observeRecent(limit)

    suspend fun record(trackId: String, positionMs: Long = 0L) = withContext(Dispatchers.IO) {
        if (trackId.isBlank()) return@withContext
        db.historyDao().insert(
            PlayHistoryEntity(
                trackId = trackId,
                playedAtEpoch = System.currentTimeMillis(),
                positionMs = positionMs,
            ),
        )
        db.historyDao().trim()
    }

    suspend fun clear() = withContext(Dispatchers.IO) { db.historyDao().clear() }
}
