package com.hoppingmall.notification.service

import org.springframework.stereotype.Repository
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Repository
class SseEmitterRepository {

    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun save(userId: Long, emitter: SseEmitter): SseEmitter {
        emitters.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(emitter)
        return emitter
    }

    fun findByUserId(userId: Long): List<SseEmitter> {
        return emitters[userId]?.toList() ?: emptyList()
    }

    fun remove(userId: Long, emitter: SseEmitter) {
        emitters.compute(userId) { _, list ->
            list?.apply { remove(emitter) }?.ifEmpty { null }
        }
    }
}
