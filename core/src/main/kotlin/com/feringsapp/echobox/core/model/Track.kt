package com.feringsapp.echobox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthorContacts(
    val bilibili: String? = null,
    val netease: String? = null,
    val soundcloud: String? = null,
    val other: String? = null,
) {
    fun any(): Boolean = listOfNotNull(bilibili, netease, soundcloud, other).any { it.isNotBlank() }
}

@Serializable
data class SeriesRef(
    val name: String = "",
    val index: Int? = null,
    val total: Int? = null,
)

/**
 * 索引里的单条曲目，字段名与仓库 schema 一一对应。
 * authorization / resource_license 用 String 而不是 enum：远端出现新值时不能让整条解析失败。
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val author: String = "",
    @SerialName("author_contacts") val authorContacts: AuthorContacts = AuthorContacts(),
    val game: String = "Undertale",
    val tags: List<String> = emptyList(),
    val series: SeriesRef? = null,
    val authorization: String = Authorization.SELF_MADE,
    @SerialName("resource_license") val resourceLicense: String = "",
    val file: String = "",
    val format: String = "",
    val duration: Int = 0,
    val cover: String = "",
    val hash: String = "",
    @SerialName("uploaded_at") val uploadedAt: String = "",
) {
    val sha256: String get() = hash.removePrefix("sha256:")
}

object Authorization {
    const val SELF_MADE = "self_made"
    const val AUTHORIZED_REPOST = "authorized_repost"

    fun label(value: String): String = when (value) {
        SELF_MADE -> "自制"
        AUTHORIZED_REPOST -> "授权转载"
        else -> value
    }
}
