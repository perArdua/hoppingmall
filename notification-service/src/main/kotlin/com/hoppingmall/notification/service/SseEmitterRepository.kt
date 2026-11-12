package com.hoppingmall.notification.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Repository
class SseEmitterRepository(
    @Value("\${notification.sse.max-connections-per-user:$DEFAULT_MAX_CONNECTIONS_PER_USER}")
    private val maxConnectionsPerUser: Int = DEFAULT_MAX_CONNECTIONS_PER_USER
) {

    companion object {
        const val DEFAULT_MAX_CONNECTIONS_PER_USER = 3
    }

    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    init {
        require(maxConnectionsPerUser > 0) { "사용자별 SSE 연결 상한은 1 이상이어야 합니다: $maxConnectionsPerUser" }
    }

    fun save(userId: Long, emitter: SseEmitter): SseEmitter {
        val evicted = mutableListOf<SseEmitter>()
        emitters.compute(userId) { _, list ->
            (list ?: CopyOnWriteArrayList()).apply {
                add(emitter)
                while (size > maxConnectionsPerUser) {
                    evicted.add(removeAt(0))
                }
            }
        }
        evicted.forEach { it.complete() }
        return emitter
    }

    fun findByUserId(userId: Long): List<SseEmitter> =
        emitters[userId]?.toList() ?: emptyList()

    fun remove(userId: Long, emitter: SseEmitter) {
        emitters.compute(userId) { _, list ->
            list?.apply { remove(emitter) }?.ifEmpty { null }
        }
    }
}
