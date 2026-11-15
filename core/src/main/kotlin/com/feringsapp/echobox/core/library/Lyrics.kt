package com.feringsapp.echobox.core.library

/** 一行歌词：时间戳 + 文本，纯数据，界面层自己算当前行。 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * LRC 解析：支持 [mm:ss]、[mm:ss.xx]、[mm:ss.xxx]，一行可挂多个时间标签。
 * 解析失败的行直接跳过，不抛异常；元信息标签（[ti:]、[ar:]、[offset:]）忽略。
 */
object LrcParser {

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val matches = timeTag.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { match ->
                val minute = match.groupValues[1].toLongOrNull() ?: return@forEach
                val second = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = match.groupValues[3].let { raw ->
                    if (raw.isEmpty()) 0L else raw.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                }
                lines += LyricLine(minute * 60_000 + second * 1_000 + fraction, text)
            }
        }
        return lines.sortedBy(LyricLine::timeMs)
    }

    /** 返回当前时间对应的行下标，没有匹配返回 -1。 */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
