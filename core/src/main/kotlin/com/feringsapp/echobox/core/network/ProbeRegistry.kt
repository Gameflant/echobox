package com.feringsapp.echobox.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次真实下载测速的结果。 */
data class ProbeResult(
    val mirrorId: String,
    /** 千字节每秒；null 表示超时或失败。 */
    val speedKbps: Double? = null,
    val timedOut: Boolean = false,
    val error: String? = null,
) {
    val ok: Boolean get() = speedKbps != null
}

/** 测速结果放内存，界面直接观察；重启 App 重新测。 */
object ProbeRegistry {

    private val _results = MutableStateFlow<Map<String, ProbeResult>>(emptyMap())
    val results: StateFlow<Map<String, ProbeResult>> = _results.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    fun put(result: ProbeResult) {
        _results.value = _results.value + (result.mirrorId to result)
    }

    fun clear() {
        _results.value = emptyMap()
    }

    /** 当前结果里速度最快的源 id。 */
    fun fastest(): String? = _results.value.values
        .filter { it.ok }
        .maxByOrNull { it.speedKbps ?: 0.0 }
        ?.mirrorId
}
