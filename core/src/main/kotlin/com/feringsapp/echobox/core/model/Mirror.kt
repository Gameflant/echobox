package com.feringsapp.echobox.core.model

data class RepoConfig(
    val owner: String,
    val repo: String,
    val branch: String = "main",
) {
    fun rawBase(): String = "https://raw.githubusercontent.com/$owner/$repo/$branch/"
    fun jsdelivrBase(): String = "https://fastly.jsdelivr.net/gh/$owner/$repo@$branch/"
    fun ghproxyRawBase(): String = "https://ghproxy.net/https://raw.githubusercontent.com/$owner/$repo/$branch/"
    fun assetBase(): String = "https://github.com/$owner/$repo/releases/download/audio/"
    fun ghproxyAssetBase(): String = "https://ghproxy.net/https://github.com/$owner/$repo/releases/download/audio/"
}

enum class MirrorKind { BUILTIN, CUSTOM }

data class Mirror(
    val id: String,
    val name: String,
    val kind: MirrorKind,
    /** raw 文件模板，必须以 / 结尾。 */
    val rawTemplate: String,
    /** Releases 资源模板，空字符串表示该镜像不支持音频。 */
    val assetTemplate: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val lastRttMs: Long? = null,
    val lastCheckEpoch: Long = 0,
    /** 界面上显示的一句话说明。 */
    val description: String = "",
)

/**
 * 候选源：单选模式，用户点哪个就用哪个，不再自动排序切换。
 * 顺序即默认展示顺序，priority 只用于测速排序时的兜底。
 */
fun defaultMirrors(repo: RepoConfig): List<Mirror> = listOf(
    Mirror(
        id = "direct",
        name = "直连",
        kind = MirrorKind.BUILTIN,
        rawTemplate = repo.rawBase(),
        assetTemplate = repo.assetBase(),
        priority = 0,
        description = "直接从 GitHub 仓库获取，通常需要代理",
    ),
    Mirror(
        id = "ghfast",
        name = "GhFast",
        kind = MirrorKind.BUILTIN,
        rawTemplate = "https://ghfast.top/" + repo.rawBase(),
        assetTemplate = "https://ghfast.top/" + repo.assetBase(),
        priority = 1,
        description = "GitHub 镜像，目前稳定性最好",
    ),
    Mirror(
        id = "ghproxy",
        name = "GhProxy",
        kind = MirrorKind.BUILTIN,
        rawTemplate = "https://ghproxy.net/" + repo.rawBase(),
        assetTemplate = "https://ghproxy.net/" + repo.assetBase(),
        priority = 2,
        description = "GitHub 镜像",
    ),
    Mirror(
        id = "ghproxy-org",
        name = "GhProxy.org",
        kind = MirrorKind.BUILTIN,
        rawTemplate = "https://gh-proxy.org/" + repo.rawBase(),
        assetTemplate = "https://gh-proxy.org/" + repo.assetBase(),
        priority = 3,
        description = "gh-proxy 的备用域名",
    ),
    Mirror(
        id = "ddlc",
        name = "DDLC 镜像",
        kind = MirrorKind.BUILTIN,
        rawTemplate = "https://gh.ddlc.top/" + repo.rawBase(),
        assetTemplate = "https://gh.ddlc.top/" + repo.assetBase(),
        priority = 4,
        description = "社区公共代理",
    ),
    Mirror(
        id = "isteed",
        name = "iSteed 镜像",
        kind = MirrorKind.BUILTIN,
        rawTemplate = "https://core.isteel.cc/" + repo.rawBase(),
        assetTemplate = "https://core.isteel.cc/" + repo.assetBase(),
        priority = 5,
        description = "社区公共代理",
    ),
    Mirror(
        id = "jsdelivr",
        name = "jsDelivr",
        kind = MirrorKind.BUILTIN,
        rawTemplate = repo.jsdelivrBase(),
        // jsDelivr 不代理 Releases 资源
        assetTemplate = "",
        priority = 6,
        description = "只加速索引与封面，不能下载音频",
    ),
)
