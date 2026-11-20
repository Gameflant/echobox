package com.feringsapp.echobox.core.settings

import android.content.Context
import java.util.UUID

/**
 * 投稿归属用的匿名设备 ID。
 * 首次上传时生成，之后固定在本机；不含任何个人信息，只用来把同一台设备的投稿聚在一起。
 */
object SubmitterId {
    private const val FILE = "submitter"
    private const val KEY = "id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, id).apply()
        return id
    }

    /** 设置页展示用，取前 8 位。 */
    fun short(context: Context): String = get(context).take(8)
}
