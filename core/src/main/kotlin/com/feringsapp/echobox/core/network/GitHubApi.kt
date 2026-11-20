package com.feringsapp.echobox.core.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 只做投稿需要的四个动作：找 release、传 asset、写文件、读文件。
 * token 只授 staging 仓库的 Contents 读写，没有删除权限。
 */
class GitHubApi(
    private val token: String,
    private val owner: String,
    private val repo: String,
) {
    data class Release(val id: Long, val uploadUrl: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS)
        .build()

    private fun req(path: String): Request.Builder =
        Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EchoBox")
            .header("Authorization", "Bearer $token")

    suspend fun releaseByTag(tag: String): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(req("/releases/tags/$tag").get().build()).execute().use { res ->
                val body = res.body.string()
                if (!res.isSuccessful) error("release 不存在（${res.code}）")
                val o = json.parseToJsonElement(body).jsonObject
                Release(
                    id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: error("release id 缺失"),
                    uploadUrl = o["upload_url"]?.jsonPrimitive?.content
                        ?.substringBefore('{') ?: error("upload_url 缺失"),
                )
            }
        }
    }

    /** 上传一个 asset，返回下载地址。onProgress 参数为 (已传, 总数)。 */
    suspend fun uploadAsset(
        release: Release,
        fileName: String,
        file: File,
        contentType: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = object : RequestBody() {
                override fun contentType() = contentType.toMediaType()
                override fun contentLength() = file.length()
                override fun writeTo(sink: BufferedSink) {
                    val total = file.length()
                    var done = 0L
                    var last = 0L
                    file.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            sink.write(buf, 0, n)
                            done += n
                            if (done - last >= 256 * 1024) {
                                last = done
                                onProgress(done, total)
                            }
                        }
                    }
                    onProgress(done, total)
                }
            }
            val url = "${release.uploadUrl}?name=${java.net.URLEncoder.encode(fileName, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EchoBox")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { res ->
                val text = res.body.string()
                if (!res.isSuccessful) error("上传失败（${res.code}）")
                json.parseToJsonElement(text).jsonObject["browser_download_url"]
                    ?.jsonPrimitive?.content ?: error("返回里没有下载地址")
            }
        }
    }

    /** 当前待审核的投稿 id 集合。目录为空时返回空集合。 */
    suspend fun listPendingIds(): Result<Set<String>> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(req("/contents/pending").get().build()).execute().use { res ->
                if (res.code == 404) return@runCatching emptySet()
                val body = res.body.string()
                if (!res.isSuccessful) error("读取 pending 失败（${res.code}）")
                val arr = json.parseToJsonElement(body).jsonArray
                arr.mapNotNull { item ->
                    item.jsonObject["name"]?.jsonPrimitive?.content
                        ?.takeIf { it.endsWith(".json") }
                        ?.removeSuffix(".json")
                }.toSet()
            }
        }
    }

    /** 写仓库文件（存在则带 sha 更新）。 */
    suspend fun putFile(
        path: String,
        bytes: ByteArray,
        message: String,
        branch: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sha = existingSha(path, branch)
            val payload = buildString {
                append("{\"message\":")
                append(quote(message))
                append(",\"content\":")
                append(quote(Base64.encodeToString(bytes, Base64.NO_WRAP)))
                if (sha != null) {
                    append(",\"sha\":")
                    append(quote(sha))
                }
                if (branch != null) {
                    append(",\"branch\":")
                    append(quote(branch))
                }
                append('}')
            }
            val request = req("/contents/$path")
                .header("Content-Type", "application/json")
                .put(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    val text = res.body.string()
                    error("写入失败（${res.code}）${text.take(200)}")
                }
            }
        }
    }

    private fun existingSha(path: String, branch: String?): String? {
        val url = "https://api.github.com/repos/$owner/$repo/contents/$path" +
            (branch?.let { "?ref=$it" } ?: "")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EchoBox")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return null
                json.parseToJsonElement(res.body.string()).jsonObject["sha"]
                    ?.jsonPrimitive?.content
            }
        }.getOrNull()
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
