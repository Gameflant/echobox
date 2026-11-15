package com.feringsapp.echobox.feature.main

import androidx.compose.runtime.staticCompositionLocalOf
import com.feringsapp.echobox.core.CoreGraph
import com.feringsapp.echobox.core.library.AudioTranscoder
import com.feringsapp.echobox.core.library.DemoLibrary
import com.feringsapp.echobox.core.library.MediaStorage
import com.feringsapp.echobox.core.repo.DownloadRepository
import com.feringsapp.echobox.core.repo.HistoryRepository
import com.feringsapp.echobox.core.repo.TrackRepository
import com.feringsapp.echobox.core.settings.LibraryStore

val LocalTrackRepository = staticCompositionLocalOf<TrackRepository> { error("TrackRepository 未提供") }

val LocalDemoLibrary = staticCompositionLocalOf<DemoLibrary> { error("DemoLibrary 未提供") }

val LocalLibraryStore = staticCompositionLocalOf<LibraryStore> { error("LibraryStore 未提供") }

val LocalMediaStorage = staticCompositionLocalOf<MediaStorage> { error("MediaStorage 未提供") }

val LocalAudioTranscoder = staticCompositionLocalOf<AudioTranscoder> { error("AudioTranscoder 未提供") }

val LocalDownloadRepository = staticCompositionLocalOf<DownloadRepository> { error("DownloadRepository 未提供") }

val LocalHistoryRepository = staticCompositionLocalOf<HistoryRepository> { error("HistoryRepository 未提供") }

/** 当前源下的封面前缀，列表里直接拼 AsyncImage 地址。 */
val LocalCoverBase = staticCompositionLocalOf { "" }

val LocalCoreGraph = staticCompositionLocalOf<CoreGraph> { error("CoreGraph 未提供") }
