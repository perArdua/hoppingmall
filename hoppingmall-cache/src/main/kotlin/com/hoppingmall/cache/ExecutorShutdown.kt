package com.hoppingmall.cache

import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal object ExecutorShutdown {

    private val log = LoggerFactory.getLogger(ExecutorShutdown::class.java)

    val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(3)

    fun closeNow(executor: ExecutorService, name: String, timeout: Duration = DEFAULT_TIMEOUT) {
        executor.shutdownNow()
        try {
            if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("캐시 스레드 풀이 {}ms 안에 끝나지 않음 [pool={}]", timeout.toMillis(), name)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
