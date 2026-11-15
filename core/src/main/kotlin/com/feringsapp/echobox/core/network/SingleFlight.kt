package com.feringsapp.echobox.core.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/** 同一 key 的并发请求合并成一次，后到的直接 await 前一个结果。 */
class SingleFlight {
    private val inFlight = ConcurrentHashMap<String, Deferred<Any>>()

    suspend fun <T : Any> run(key: String, block: suspend () -> T): T {
        inFlight[key]?.let { @Suppress("UNCHECKED_CAST") return it.await() as T }
        return coroutineScope {
            val deferred = async(start = CoroutineStart.LAZY) { block() as Any }
            val previous = inFlight.putIfAbsent(key, deferred)
            if (previous != null) {
                deferred.cancel()
                @Suppress("UNCHECKED_CAST")
                previous.await() as T
            } else {
                try {
                    deferred.await() as T
                } finally {
                    inFlight.remove(key, deferred)
                }
            }
        }
    }
}
