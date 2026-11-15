package com.feringsapp.echobox.core.config

/** 所有远端 URL 的准入规则，只有 MirrorResolver 允许用它拼 URL。 */
object UrlPolicy {
    /** 内置 + 允许用户显式添加的镜像 host。自定义模板的 host 会追加进运行时集合。 */
    val builtinHosts: Set<String> = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "fastly.jsdelivr.net",
        "cdn.jsdelivr.net",
        "ghproxy.net",
        "ghfast.top",
        "gh-proxy.org",
        "gh.ddlc.top",
        "core.isteel.cc",
    )

    private val blockedHostSuffixes = listOf(".local", ".internal", ".localhost", "localhost")

    /** 索引/封面/推荐/统计的相对路径，禁止 ..、//、协议、查询串。 */
    private val rawPath = Regex("^(index|covers)/[A-Za-z0-9][A-Za-z0-9._/-]{0,255}$")
    private val topLevelPath = Regex("^(featured|stats)\\.json$")
    private val fileName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

    fun isAllowedHost(host: String, extraHosts: Set<String> = emptySet()): Boolean {
        val h = host.lowercase()
        if (h.isBlank()) return false
        if (blockedHostSuffixes.any { h == it || h.endsWith(it) }) return false
        // IP 字面量一律拒绝
        if (h.matches(Regex("^[0-9.]+$")) || h.contains(":")) return false
        return h in builtinHosts || h in extraHosts
    }

    fun isValidRawPath(path: String): Boolean = rawPath.matches(path) || topLevelPath.matches(path)

    fun isValidFileName(name: String): Boolean = fileName.matches(name)

    fun hostOf(url: String): String? = runCatching {
        val uri = java.net.URI(url)
        if (uri.scheme != "https") return null
        if (uri.userInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        uri.host
    }.getOrNull()
}
