package com.feringsapp.echobox.core.network

import com.feringsapp.echobox.core.model.Mirror
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 真实下载测速：每个源拉 512KB 算速度，3.5 秒没拉完判超时。
 * 必须走 SafeHttp 才能跟随 GitHub 资产的 302 跳转（裸客户端关着重定向）。
 */
class MirrorProbe(
    private val http: SafeHttp,
    private val resolver: MirrorResolver,
) {
    suspend fun probeAll(): List<ProbeResult> = coroutineScope {
        ProbeRegistry.setRunning(true)
        ProbeRegistry.clear()
        val mirrors = resolver.mirrors().filter { it.enabled }
        val results = mirrors.map { mirror -> async { probeOne(mirror) } }.awaitAll()
        ProbeRegistry.setRunning(false)
        results
    }

    suspend fun probeOne(mirror: Mirror): ProbeResult = withContext(Dispatchers.IO) {
        val url = runCatching {
            if (mirror.assetTemplate.isNotBlank()) {
                resolver.assetUrl(mirror, PROBE_FILE)
            } else {
                resolver.rawUrl(mirror, "index/meta.json")
            }
        }.getOrNull()
        if (url == null) {
            val failed = ProbeResult(mirror.id, error = "地址不可用")
            ProbeRegistry.put(failed)
            return@withContext failed
        }

        val start = System.nanoTime()
        var bytes = 0L
        var failure: String? = null
        val finished = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                http.read(url) { handle ->
                    val stream = handle.stream
                    if (stream == null) {
                        failure = "空响应"
                        return@read false
                    }
                    stream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (bytes < LIMIT_BYTES) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            bytes += read
                        }
                    }
                    true
                }
            }.getOrElse {
                failure = it.message ?: it::class.simpleName
                false
            }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        val result = when {
            finished == null -> ProbeResult(mirror.id, timedOut = true)
            finished == false || bytes < MIN_BYTES -> ProbeResult(mirror.id, error = failure ?: "下载失败")
            else -> ProbeResult(mirror.id, speedKbps = bytes / 1024.0 / (elapsed / 1000.0).coerceAtLeast(0.001))
        }
        ProbeRegistry.put(result)
        if (result.ok) {
            runCatching { resolver.recordRtt(mirror.id, elapsed.toLong()) }
        }
        result
    }

    private companion object {
        const val TIMEOUT_MS = 3_500L
        const val LIMIT_BYTES = 512L * 1024
        const val MIN_BYTES = 32L * 1024
        /** 用专辑第一首当探测文件，比索引更能反映音频是否可下。 */
        const val PROBE_FILE = "sp-01.wav"
    }
}
