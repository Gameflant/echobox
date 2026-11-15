package com.feringsapp.echobox.core.network

import com.feringsapp.echobox.core.config.UrlPolicy
import com.feringsapp.echobox.core.db.MirrorDao
import com.feringsapp.echobox.core.db.MirrorEntity
import com.feringsapp.echobox.core.model.Mirror
import com.feringsapp.echobox.core.model.MirrorKind
import com.feringsapp.echobox.core.model.RepoConfig
import com.feringsapp.echobox.core.model.defaultMirrors
import com.feringsapp.echobox.core.settings.NetworkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI

/**
 * 唯一允许拼接远端 URL 的地方。
 * 规则：https + 443 + host 白名单 + 相对路径白名单，任一不满足直接抛 IOException。
 * 当前选中的源由 [NetworkStore] 决定，单选不自动切换。
 */
class MirrorResolver(
    private val repo: RepoConfig,
    private val mirrorDao: MirrorDao,
    private val networkStore: NetworkStore,
) {
    @Volatile
    private var extraHosts: Set<String> = emptySet()

    @Volatile
    private var cached: List<Mirror> = emptyList()

    @Volatile
    private var selectedId: String = "direct"

    suspend fun ensureDefaults() = withContext(Dispatchers.IO) {
        val existing = mirrorDao.all().associateBy { it.id }
        val builtin = defaultMirrors(repo).map { mirror ->
            val old = existing[mirror.id]
            // 模板、优先级以代码为准，只保留旧记录里的测速结果
            if (old != null) {
                mirror.toEntity().copy(lastRttMs = old.lastRttMs, lastCheckEpoch = old.lastCheckEpoch)
            } else {
                mirror.toEntity()
            }
        }
        mirrorDao.upsert(builtin)
        // 旧版本留下的内置源（builtin-raw 等）不再使用，直接清掉，避免列表重复
        mirrorDao.deleteBuiltinNotIn(builtin.map { it.id })
        refreshHosts()
        cached = mirrorDao.all().map { entity -> entity.toModel(descriptionsFor()[entity.id].orEmpty()) }
        selectedId = runCatching { networkStore.current() }.getOrDefault("direct")
    }

    suspend fun refreshHosts() = withContext(Dispatchers.IO) {
        extraHosts = mirrorDao.all().mapNotNull { UrlPolicy.hostOf(it.rawTemplate) }.toSet()
        if (cached.isEmpty()) cached = mirrorDao.all().map { it.toModel() }
    }

    suspend fun mirrors(): List<Mirror> = withContext(Dispatchers.IO) {
        val descriptions = descriptionsFor()
        val list = mirrorDao.all().map { entity -> entity.toModel(descriptions[entity.id].orEmpty()) }
        cached = list
        list
    }

    private fun descriptionsFor(): Map<String, String> =
        defaultMirrors(repo).associate { it.id to it.description }

    /** 用户点了某个源：写盘并立刻生效。 */
    suspend fun select(id: String) {
        networkStore.select(id)
        selectedId = id
        if (cached.isEmpty()) runCatching { mirrors() }
    }

    fun selectedId(): String = selectedId

    fun selected(): Mirror {
        val list = cached
        return list.firstOrNull { it.id == selectedId }
            ?: list.firstOrNull { it.assetTemplate.isNotBlank() }
            ?: list.first()
    }

    /** 有测速结果的按速度排，没测过的排后面按 priority。 */
    suspend fun ordered(): List<Mirror> = withContext(Dispatchers.IO) {
        mirrorDao.enabled().map { it.toModel() }.sortedWith(
            compareBy({ it.lastRttMs == null }, { it.lastRttMs ?: 0L }, { it.priority }),
        )
    }

    fun isHostAllowed(host: String): Boolean = UrlPolicy.isAllowedHost(host, extraHosts)

    fun rawUrl(mirror: Mirror, relativePath: String): String {
        if (!UrlPolicy.isValidRawPath(relativePath)) throw IOException("非法索引路径: $relativePath")
        return compose(mirror.rawTemplate, relativePath)
    }

    fun assetUrl(mirror: Mirror, fileName: String): String {
        if (!UrlPolicy.isValidFileName(fileName)) throw IOException("非法资源名: $fileName")
        if (mirror.assetTemplate.isBlank()) throw IOException("镜像 ${mirror.name} 不支持资源下载")
        return compose(mirror.assetTemplate, fileName)
    }

    fun coverUrl(mirror: Mirror, coverName: String): String = rawUrl(mirror, "covers/$coverName")

    // ---- 基于当前选中源的快捷方法 ----

    fun resolveRaw(relativePath: String): String = rawUrl(selected(), relativePath)

    fun resolveAsset(fileName: String): String = assetUrl(selected(), fileName)

    /** 封面为空或当前源不支持时返回空串，界面自动退回占位色块。 */
    fun resolveCover(coverName: String): String {
        if (coverName.isBlank()) return ""
        return runCatching { coverUrl(selected(), coverName) }.getOrDefault("")
    }

    /**
     * 封面基准地址：固定走 jsDelivr。
     * 封面是图片 CDN 场景，和「音频能不能下」无关，绑到选中的源反而会因为 raw 被墙整片加载失败。
     */
    fun coverBase(): String = repo.jsdelivrBase() + "covers/"

    private fun compose(template: String, path: String): String {
        val base = if (template.endsWith("/")) template else "$template/"
        val url = base + path
        val host = UrlPolicy.hostOf(url) ?: throw IOException("URL 不是合法 HTTPS: $url")
        if (!UrlPolicy.isAllowedHost(host, extraHosts)) throw IOException("镜像 host 未在白名单: $host")
        return url
    }

    /** 校验用户自定义模板，允许它把自己的 host 加入白名单。 */
    fun validateCustomTemplate(template: String): Boolean {
        val uri = runCatching { URI(template) }.getOrNull() ?: return false
        if (uri.scheme != "https") return false
        if (uri.userInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        if (host.matches(Regex("^[0-9.]+$"))) return false
        return !host.endsWith(".local") && !host.endsWith(".internal") && host != "localhost"
    }

    suspend fun addCustom(mirror: Mirror) = withContext(Dispatchers.IO) {
        mirrorDao.upsertOne(mirror.toEntity())
        refreshHosts()
        mirrors()
    }

    suspend fun removeCustom(id: String) = withContext(Dispatchers.IO) {
        mirrorDao.deleteCustom(id)
        refreshHosts()
        mirrors()
    }

    suspend fun resetDefaults() = withContext(Dispatchers.IO) {
        mirrorDao.clearCustom()
        mirrorDao.upsert(defaultMirrors(repo).map { it.toEntity() })
        refreshHosts()
        mirrors()
    }

    suspend fun recordRtt(id: String, rtt: Long) = withContext(Dispatchers.IO) {
        mirrorDao.all().firstOrNull { it.id == id }?.let {
            mirrorDao.upsertOne(it.copy(lastRttMs = rtt, lastCheckEpoch = System.currentTimeMillis()))
        }
    }
}

fun MirrorEntity.toModel(description: String = ""): Mirror = Mirror(
    id = id,
    name = name,
    kind = runCatching { MirrorKind.valueOf(kind) }.getOrDefault(MirrorKind.CUSTOM),
    rawTemplate = rawTemplate,
    assetTemplate = assetTemplate,
    enabled = enabled,
    priority = priority,
    lastRttMs = lastRttMs,
    lastCheckEpoch = lastCheckEpoch,
    description = description,
)

fun Mirror.toEntity(): MirrorEntity = MirrorEntity(
    id = id,
    name = name,
    kind = kind.name,
    rawTemplate = rawTemplate,
    assetTemplate = assetTemplate,
    enabled = enabled,
    priority = priority,
    lastRttMs = lastRttMs,
    lastCheckEpoch = lastCheckEpoch,
)
