package com.feringsapp.echobox.core.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 检查更新与下载 APK。只依赖 GitHub 公共 REST，不需要登录。 */
class UpdateRepository(
    private val context: Context,
    private val owner: String,
    private val repo: String,
    private val token: String,
    private val currentVersion: String,
    private val currentVersionCode: Int = 0,
) {

    data class ReleaseInfo(
        val tag: String,
        val name: String,
        val notes: String,
        val htmlUrl: String,
        val publishedAt: String,
        val apkUrl: String,
        val apkName: String,
        val apkSize: Long,
        /** GitHub 返回的 digest，形如 sha256:xxxx；没有就是空串。 */
        val digest: String,
        val version: String,
        /** 发布说明里写的 versionCode，没有就是 0。 */
        val code: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun check(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EchoBox/$currentVersion")
                .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
                .build()
            client.newCall(req).execute().use { res ->
                if (res.code == 404) return@runCatching null
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val body = res.body.string()
                val root = json.parseToJsonElement(body).jsonObject
                val tag = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
                val version = tag.removePrefix("v").removePrefix("V")
                val assets = root["assets"]?.jsonArray ?: return@runCatching null
                val apk = assets.firstOrNull {
                    it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true
                }?.jsonObject ?: return@runCatching null
                ReleaseInfo(
                    tag = tag,
                    name = root["name"]?.jsonPrimitive?.content.orEmpty(),
                    notes = root["body"]?.jsonPrimitive?.content.orEmpty(),
                    htmlUrl = root["html_url"]?.jsonPrimitive?.content.orEmpty(),
                    publishedAt = root["published_at"]?.jsonPrimitive?.content.orEmpty(),
                    apkUrl = apk["browser_download_url"]?.jsonPrimitive?.content.orEmpty(),
                    apkName = apk["name"]?.jsonPrimitive?.content.orEmpty(),
                    apkSize = apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    digest = apk["digest"]?.jsonPrimitive?.content.orEmpty(),
                    version = version,
                    code = parseVersionCode(root["body"]?.jsonPrimitive?.content.orEmpty()),
                )
            }
        }
    }

    /** 发布说明里带 `versionCode: 4` 时按它比较，否则退回版本号字符串比较。 */
    private fun parseVersionCode(body: String): Int =
        Regex("versionCode\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    /** true 表示远端比本地新。 */
    fun isNewer(info: ReleaseInfo): Boolean {
        if (info.code > 0 && currentVersionCode > 0) return info.code > currentVersionCode
        return isNewer(info.version, currentVersion)
    }

    fun isNewer(remote: String, local: String = currentVersion): Boolean {
        val a = parseVersion(remote)
        val b = parseVersion(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        v.removePrefix("v").removePrefix("V").split('.', '-', '_')
            .mapNotNull { s -> s.takeWhile { it.isDigit() }.toIntOrNull() }

    suspend fun download(info: ReleaseInfo, onProgress: (Long, Long) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "update").apply { mkdirs() }
                val target = File(dir, info.apkName.ifBlank { "echobox-${info.version}.apk" })
                val part = File(dir, target.name + ".part")
                if (part.exists()) part.delete()

                val req = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "EchoBox/$currentVersion")
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) error("HTTP ${res.code}")
                    val total = res.body.contentLength().takeIf { it > 0 } ?: info.apkSize
                    res.body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var last = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (done - last >= 512 * 1024) {
                                    last = done
                                    onProgress(done, total)
                                }
                            }
                            output.flush()
                            onProgress(done, total)
                        }
                    }
                }
                if (part.length() <= 0) error("下载内容为空")
                if (info.digest.startsWith("sha256:")) {
                    val want = info.digest.removePrefix("sha256:").lowercase()
                    val got = sha256(part).lowercase()
                    if (want != got) {
                        part.delete()
                        error("校验失败")
                    }
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                target
            }.onFailure { Log.w("EchoBox", "更新下载失败", it) }
        }

    fun install(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
