package com.feringsapp.echobox.core.sync

import com.feringsapp.echobox.core.model.Featured
import com.feringsapp.echobox.core.model.IncrementalEntry
import com.feringsapp.echobox.core.model.IncrementalManifest
import com.feringsapp.echobox.core.model.IndexMeta
import com.feringsapp.echobox.core.model.Stats
import com.feringsapp.echobox.core.model.Track
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream

/**
 * 索引统一走流式解码：调用方拿到 Sequence 后逐条写库，
 * 内存里不会出现整份 JSON。
 */
@OptIn(ExperimentalSerializationApi::class)
class IndexParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
) {
    fun tracks(stream: InputStream): Sequence<Track> = json.decodeToSequence<Track>(stream)

    fun incremental(stream: InputStream): Sequence<IncrementalEntry> =
        json.decodeToSequence<IncrementalEntry>(stream)

    fun meta(stream: InputStream): IndexMeta = readText(stream) { json.decodeFromString<IndexMeta>(it) }

    fun manifest(stream: InputStream): IncrementalManifest =
        readText(stream) { json.decodeFromString<IncrementalManifest>(it) }

    fun featured(stream: InputStream): Featured = readText(stream) { json.decodeFromString<Featured>(it) }

    fun stats(stream: InputStream): Stats = readText(stream) { json.decodeFromString<Stats>(it) }

    private inline fun <T> readText(stream: InputStream, block: (String) -> T): T =
        stream.use { block(it.readBytes().decodeToString()) }
}
