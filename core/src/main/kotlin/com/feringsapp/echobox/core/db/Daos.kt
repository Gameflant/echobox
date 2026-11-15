package com.feringsapp.echobox.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY uploadedAtEpoch DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY statsDownloads DESC, statsPlays DESC LIMIT :limit")
    fun observeHottest(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observeById(id: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query(
        "SELECT * FROM tracks WHERE searchBlob LIKE '%' || :query || '%' " +
            "ORDER BY statsDownloads DESC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE searchBlob LIKE '%' || :query || '%' ORDER BY uploadedAtEpoch DESC LIMIT :limit")
    fun observeSearch(query: String, limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT t.* FROM tracks t INNER JOIN track_tags g ON g.trackId = t.id WHERE g.tag = :tag ORDER BY t.uploadedAtEpoch DESC LIMIT :limit")
    fun observeByTag(tag: String, limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE seriesName = :name ORDER BY seriesIndex ASC")
    fun observeBySeries(name: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT id FROM tracks WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): String?

    @Query("SELECT * FROM tracks")
    suspend fun all(): List<TrackEntity>

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(items: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE tracks SET statsDownloads = :downloads, statsPlays = :plays WHERE id = :id")
    suspend fun updateStats(id: String, downloads: Int, plays: Int)
}

@Dao
interface TagDao {
    @Query("SELECT tag, COUNT(*) AS count FROM track_tags GROUP BY tag ORDER BY count DESC")
    fun observeAll(): Flow<List<TagCount>>

    @Query("SELECT tag, COUNT(*) AS count FROM track_tags GROUP BY tag ORDER BY count DESC")
    suspend fun all(): List<TagCount>

    @Query("SELECT DISTINCT trackId FROM track_tags WHERE tag IN (:tags)")
    suspend fun trackIdsWithAnyTag(tags: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TrackTagEntity>)

    @Query("DELETE FROM track_tags WHERE trackId IN (:trackIds)")
    suspend fun deleteByTrackIds(trackIds: List<String>)

    @Query("DELETE FROM track_tags WHERE trackId = :trackId")
    suspend fun deleteByTrack(trackId: String)
}

data class TagCount(val tag: String, val count: Int)

@Dao
interface MetaDao {
    @Query("SELECT * FROM http_cache WHERE url = :url")
    suspend fun cache(url: String): HttpCacheEntity?

    @Upsert
    suspend fun putCache(entity: HttpCacheEntity)

    @Query("SELECT value FROM sync_state WHERE key = :key")
    suspend fun getState(key: String): String?

    @Upsert
    suspend fun putState(entity: SyncStateEntity)

    @Query("DELETE FROM http_cache WHERE url = :url")
    suspend fun dropCache(url: String)
}

@Dao
interface MirrorDao {
    @Query("SELECT * FROM mirrors ORDER BY priority ASC")
    fun observeAll(): Flow<List<MirrorEntity>>

    @Query("SELECT * FROM mirrors WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun enabled(): List<MirrorEntity>

    @Query("SELECT * FROM mirrors ORDER BY priority ASC")
    suspend fun all(): List<MirrorEntity>

    @Upsert
    suspend fun upsert(items: List<MirrorEntity>)

    @Upsert
    suspend fun upsertOne(item: MirrorEntity)

    @Query("DELETE FROM mirrors WHERE id = :id AND kind = 'CUSTOM'")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM mirrors WHERE kind = 'CUSTOM'")
    suspend fun clearCustom()

    @Query("DELETE FROM mirrors WHERE kind = 'BUILTIN' AND id NOT IN (:ids)")
    suspend fun deleteBuiltinNotIn(ids: List<String>)
}

@Dao
interface SubmissionDao {
    @Query("SELECT * FROM submissions ORDER BY submittedAtEpoch DESC")
    fun observeAll(): Flow<List<SubmissionEntity>>

    @Query("SELECT * FROM submissions WHERE recordId = :recordId")
    suspend fun byId(recordId: String): SubmissionEntity?

    @Upsert
    suspend fun upsert(item: SubmissionEntity)

    @Query("DELETE FROM submissions WHERE recordId = :recordId")
    suspend fun delete(recordId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY queueOrder DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    fun observe(trackId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun get(trackId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'DONE'")
    suspend fun doneCount(): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM play_history ORDER BY playedAtEpoch DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    @Insert
    suspend fun insert(item: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Query("DELETE FROM play_history WHERE id NOT IN (SELECT id FROM play_history ORDER BY playedAtEpoch DESC LIMIT 200)")
    suspend fun trim()
}
