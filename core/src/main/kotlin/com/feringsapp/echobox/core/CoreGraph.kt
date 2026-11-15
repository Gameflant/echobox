package com.feringsapp.echobox.core

import android.content.Context
import android.util.Log
import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.db.EchoDatabaseFactory
import com.feringsapp.echobox.core.library.AudioTranscoder
import com.feringsapp.echobox.core.library.MediaStorage
import com.feringsapp.echobox.core.model.RepoConfig
import com.feringsapp.echobox.core.network.MirrorProbe
import com.feringsapp.echobox.core.network.MirrorResolver
import com.feringsapp.echobox.core.network.SafeHttp
import com.feringsapp.echobox.core.network.SingleFlight
import com.feringsapp.echobox.core.network.createHttpClient
import com.feringsapp.echobox.core.repo.DownloadRepository
import com.feringsapp.echobox.core.repo.HistoryRepository
import com.feringsapp.echobox.core.repo.SubmissionRepository
import com.feringsapp.echobox.core.repo.TrackRepository
import com.feringsapp.echobox.core.library.DemoLibrary
import com.feringsapp.echobox.core.settings.AppearanceStore
import com.feringsapp.echobox.core.settings.LibraryStore
import com.feringsapp.echobox.core.settings.NetworkStore
import com.feringsapp.echobox.core.sync.IndexSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手写依赖容器：全部 lazy，Application.onCreate 里不做任何 IO。
 * 真正的初始化（建库、读镜像）发生在第一次 sync 或第一次进入列表时。
 */
class CoreGraph(
    private val context: Context,
    val repoConfig: RepoConfig,
) {
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    val database: EchoDatabase by lazy { EchoDatabaseFactory.create(context) }

    val httpClient by lazy { createHttpClient() }

    val networkStore: NetworkStore by lazy { NetworkStore(context) }

    val mirrorResolver: MirrorResolver by lazy { MirrorResolver(repoConfig, database.mirrorDao(), networkStore) }

    val singleFlight by lazy { SingleFlight() }

    /** 下载/测速用的原生通道：和 Ktor 客户端同一套重定向与 host 校验规则。 */
    val rawHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val safeHttp: SafeHttp by lazy {
        SafeHttp(httpClient, rawHttpClient) { host -> mirrorResolver.isHostAllowed(host) }
    }

    val indexSyncer: IndexSyncer by lazy { IndexSyncer(database, safeHttp, mirrorResolver) }

    val trackRepository: TrackRepository by lazy { TrackRepository(database) }

    val submissionRepository: SubmissionRepository by lazy { SubmissionRepository(database) }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(context, database, safeHttp, mirrorResolver)
    }

    val historyRepository: HistoryRepository by lazy { HistoryRepository(database) }

    val appearanceStore: AppearanceStore by lazy { AppearanceStore(context) }

    val libraryStore: LibraryStore by lazy { LibraryStore(context) }

    val demoLibrary: DemoLibrary by lazy { DemoLibrary(context, database) }

    val mediaStorage: MediaStorage by lazy { MediaStorage(context) }

    val audioTranscoder: AudioTranscoder by lazy { AudioTranscoder(context) }

    val mirrorProbe: MirrorProbe by lazy { MirrorProbe(safeHttp, mirrorResolver) }

    /** 启动后调用：默认镜像入库 + 后台测速 + 后台增量同步，都不阻塞首帧。 */
    fun warmUp() {
        appScope.launch {
            runCatching { demoLibrary.ensureImported() }
                .onFailure { Log.w("EchoBox", "示例曲库导入失败", it) }
            runCatching { mirrorResolver.ensureDefaults() }
                .onFailure { Log.w("EchoBox", "镜像初始化失败", it) }
            runCatching { mirrorProbe.probeAll() }
                .onFailure { Log.w("EchoBox", "镜像测速失败", it) }
            runCatching { indexSyncer.sync() }
                .onSuccess { Log.i("EchoBox", "索引同步: $it") }
                .onFailure { Log.w("EchoBox", "索引同步失败", it) }
        }
    }
}
