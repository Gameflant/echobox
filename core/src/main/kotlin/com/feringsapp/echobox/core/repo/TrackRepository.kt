package com.feringsapp.echobox.core.repo

import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.db.TrackEntity
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.core.sync.normalizeQuery
import com.feringsapp.echobox.core.sync.searchBlob
import com.feringsapp.echobox.core.sync.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackRepository(private val db: EchoDatabase) {
    private val trackDao get() = db.trackDao()

    fun observeLatest(limit: Int = 60): Flow<List<Track>> =
        trackDao.observeLatest(limit).map { rows -> rows.map(TrackEntity::toDomain) }

    fun observeHottest(limit: Int = 60): Flow<List<Track>> =
        trackDao.observeHottest(limit).map { rows -> rows.map(TrackEntity::toDomain) }

    fun observeTrack(id: String): Flow<Track?> =
        trackDao.observeById(id).map { it?.toDomain() }

    fun observeByTag(tag: String, limit: Int = 80): Flow<List<Track>> =
        trackDao.observeByTag(tag, limit).map { rows -> rows.map(TrackEntity::toDomain) }

    fun observeBySeries(name: String): Flow<List<Track>> =
        trackDao.observeBySeries(name).map { rows -> rows.map(TrackEntity::toDomain) }

    fun search(rawQuery: String, limit: Int = 50): Flow<List<Track>> {
        val query = normalizeQuery(rawQuery)
        return if (query.isBlank()) {
            trackDao.observeLatest(limit)
        } else {
            trackDao.observeSearch(query, limit)
        }.map { rows -> rows.map(TrackEntity::toDomain) }
    }

    suspend fun findIdBySha256(sha256: String): String? = trackDao.findBySha256(sha256.lowercase())

    suspend fun count(): Int = trackDao.count()

    suspend fun byId(id: String): Track? = trackDao.getById(id)?.toDomain()
}
