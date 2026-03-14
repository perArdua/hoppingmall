package com.hoppingmall.notification.service

import org.springframework.stereotype.Repository
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Repository
class SseEmitterRepository {

    private val emitters = ConcurrentHashMap<Long, MutableList<SseEmitter>>()

    fun save(userId: Long, emitter: SseEmitter): SseEmitter {
        emitters.computeIfAbsent(userId) { mutableListOf() }.add(emitter)
        return emitter
    }

    fun findByUserId(userId: Long): List<SseEmitter> {
        return emitters[userId]?.toList() ?: emptyList()
    }

    fun remove(userId: Long, emitter: SseEmitter) {
        emitters[userId]?.remove(emitter)
        if (emitters[userId]?.isEmpty() == true) {
            emitters.remove(userId)
        }
    }
}
