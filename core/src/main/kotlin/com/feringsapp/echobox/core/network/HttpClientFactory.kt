package com.feringsapp.echobox.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    // 关闭自动重定向：每一跳都由 SafeHttp 重新校验 host（防 SSRF）
    followRedirects = false
    expectSuccess = false

    engine {
        config {
            followRedirects(false)
            retryOnConnectionFailure(true)
        }
    }

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }

    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        // 不限制整个请求的时长：音频动辄几十 MB，30 秒总超时会把下载从中途掐断。
        // 真卡死由 socketTimeout 兜底。
        requestTimeoutMillis = Long.MAX_VALUE // 等价于 HttpTimeout.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = 30_000
    }

    defaultRequest {
        headers.append(HttpHeaders.UserAgent, "EchoBox/0.1 (Android)")
        headers.append(HttpHeaders.Accept, "application/json, text/plain, */*")
    }
}
