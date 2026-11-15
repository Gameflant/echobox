package com.feringsapp.echobox.core.sync

import com.feringsapp.echobox.core.db.TrackEntity
import com.feringsapp.echobox.core.model.AuthorContacts
import com.feringsapp.echobox.core.model.SeriesRef
import com.feringsapp.echobox.core.model.Track
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
private val controlChars = Regex("[\\u0000-\\u001F\\u007F]")

fun Track.toEntity(now: Long = System.currentTimeMillis()): TrackEntity = TrackEntity(
    id = id,
    title = title.trim(),
    author = author.trim(),
    game = game.trim().ifBlank { "Undertale" },
    authorContacts = json.encodeToString(authorContacts),
    tagsJson = json.encodeToString(tags),
    seriesName = series?.name?.takeIf { it.isNotBlank() },
    seriesIndex = series?.index,
    seriesTotal = series?.total,
    authorization = authorization,
    resourceLicense = resourceLicense,
    fileName = file,
    format = format.lowercase(),
    durationSec = duration,
    coverName = cover,
    sha256 = sha256.lowercase(),
    uploadedAtEpoch = runCatching { Instant.parse(uploadedAt).toEpochMilli() }.getOrDefault(0L),
    statsDownloads = 0,
    statsPlays = 0,
    searchBlob = searchBlob(),
    updatedAtEpoch = now,
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    author = author,
    authorContacts = runCatching { json.decodeFromString<AuthorContacts>(authorContacts) }
        .getOrDefault(AuthorContacts()),
    game = game,
    tags = runCatching { json.decodeFromString<List<String>>(tagsJson) }
        .getOrDefault(emptyList()),
    series = seriesName?.let { SeriesRef(it, seriesIndex, seriesTotal) },
    authorization = authorization,
    resourceLicense = resourceLicense,
    file = fileName,
    format = format,
    duration = durationSec,
    cover = coverName,
    hash = if (sha256.isBlank()) "" else "sha256:$sha256",
    uploadedAt = runCatching { Instant.ofEpochMilli(uploadedAtEpoch).toString() }.getOrDefault(""),
)

fun Track.searchBlob(): String = buildString {
    append(title.lowercase()).append(' ')
    append(author.lowercase()).append(' ')
    append(game.lowercase()).append(' ')
    series?.name?.let { append(it.lowercase()).append(' ') }
    tags.forEach { append(it.lowercase()).append(' ') }
}.replace(controlChars, " ").replace(Regex("\\s+"), " ").trim()

/** 搜索输入归一化：去控制字符、小写、限长，防 LIKE 注入与超长串。 */
fun normalizeQuery(raw: String): String =
    raw.replace(controlChars, "").trim().lowercase().take(64)

fun TrackEntity.toTagEntities(): List<com.feringsapp.echobox.core.db.TrackTagEntity> =
    toDomain().tags.map { com.feringsapp.echobox.core.db.TrackTagEntity(trackId = id, tag = it) }
