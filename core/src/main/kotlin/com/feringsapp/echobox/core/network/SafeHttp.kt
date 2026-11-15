package com.feringsapp.echobox.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI

data class StreamHandle(
    val stream: InputStream?,
    val etag: String?,
    val lastModified: String?,
    val notModified: Boolean,
    /** Content-Length，拿不到时为 -1。 */
    val contentLength: Long = -1L,
)

/** 带逐跳校验的 GET，重定向最多 5 次，每一跳都重新过 host 白名单。 */
class SafeHttp(
    private val client: HttpClient,
    private val rawClient: OkHttpClient,
    private val isHostAllowed: (String) -> Boolean,
) {
    /**
     * 原生 OkHttp 通道，给下载用。
     *
     * Ktor 的 `bodyAsChannel().toInputStream()` 每次读都要 runBlocking 拉一小块，
     * 实测吞吐只有几十 KB/s；播放器走原生 socket 能跑 900 KB/s，下载必须也走原生。
     * 重定向仍在这里逐跳校验，并把 Call 交给调用方，暂停时能真正掐断阻塞的 read。
     */
    fun openRaw(
        url: String,
        headers: Map<String, String> = emptyMap(),
        onCall: (Call) -> Unit = {},
    ): RawHandle {
        var current = url
        var hops = 0
        while (true) {
            if (hops > MAX_HOPS) throw IOException("重定向次数过多: $url")
            val builder = Request.Builder()
                .url(current)
                .header("User-Agent", "EchoBox/0.1 (Android)")
            headers.forEach { (key, value) -> builder.header(key, value) }
            val call = rawClient.newCall(builder.build())
            onCall(call)
            var response: okhttp3.Response? = null
            try {
                response = call.execute()
                val code = response.code
                if (code in 300..399) {
                    val location = response.header("Location")
                    response.close()
                    response = null
                    if (location == null) throw IOException("重定向缺少 Location")
                    current = resolveRedirect(current, location)
                    hops++
                    continue
                }
                if (code !in 200..299) {
                    response.close()
                    response = null
                    throw IOException("HTTP $code: $current")
                }
                val body = response.body
                if (body == null) {
                    response.close()
                    response = null
                    throw IOException("空响应: $current")
                }
                return RawHandle(
                    code = code,
                    stream = body.byteStream(),
                    contentLength = body.contentLength(),
                    contentRange = response.header("Content-Range"),
                    response = response,
                )
            } catch (e: Throwable) {
                response?.close()
                throw e
            }
        }
    }

    /** 原生流句柄，调用方负责 close。 */
    class RawHandle(
        val code: Int,
        val stream: InputStream,
        val contentLength: Long,
        val contentRange: String?,
        private val response: okhttp3.Response,
    ) : Closeable {
        override fun close() {
            runCatching { stream.close() }
            runCatching { response.close() }
        }
    }
    suspend fun <T> read(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
        block: suspend (StreamHandle) -> T,
    ): T {
        var current = url
        var hops = 0
        while (true) {
            if (hops > MAX_HOPS) throw IOException("重定向次数过多: $url")
            val outcome = client.prepareGet(current) {
                etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
            }.execute { response ->
                when {
                    response.status == HttpStatusCode.NotModified -> {
                        Outcome.Value(
                            block(
                                StreamHandle(
                                    stream = null,
                                    etag = etag,
                                    lastModified = response.headers[HttpHeaders.LastModified],
                                    notModified = true,
                                ),
                            ),
                        )
                    }

                    response.status.value in 300..399 -> {
                        Outcome.Redirect(response.headers[HttpHeaders.Location])
                    }

                    response.status.isSuccess() -> {
                        Outcome.Value(
                            block(
                                StreamHandle(
                                    stream = response.bodyAsChannel().toInputStream(),
                                    etag = response.headers[HttpHeaders.ETag],
                                    lastModified = response.headers[HttpHeaders.LastModified],
                                    notModified = false,
                                    contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L,
                                ),
                            ),
                        )
                    }

                    else -> throw IOException("HTTP ${response.status.value}: $current")
                }
            }
            when (outcome) {
                is Outcome.Redirect -> {
                    val location = outcome.location ?: throw IOException("重定向缺少 Location")
                    current = resolveRedirect(current, location)
                    hops++
                }

                is Outcome.Value -> return outcome.value
            }
        }
    }

    private fun resolveRedirect(base: String, location: String): String {
        val resolved = runCatching { URI(base).resolve(location).toString() }.getOrNull()
            ?: throw IOException("非法重定向地址")
        val uri = runCatching { URI(resolved) }.getOrNull() ?: throw IOException("非法重定向地址")
        if (uri.scheme != "https") throw IOException("重定向到非 HTTPS: $resolved")
        if (uri.userInfo != null) throw IOException("重定向含 userinfo")
        if (uri.port != -1 && uri.port != 443) throw IOException("重定向到非 443 端口")
        val host = uri.host?.lowercase() ?: throw IOException("重定向缺少 host")
        if (!isHostAllowed(host)) throw IOException("重定向 host 不在白名单: $host")
        return resolved
    }

    private sealed interface Outcome<out T> {
        data class Value<T>(val value: T) : Outcome<T>
        data class Redirect(val location: String?) : Outcome<Nothing>
    }

    private companion object {
        const val MAX_HOPS = 5
    }
}
