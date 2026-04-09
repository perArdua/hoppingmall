package com.hoppingmall.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@DisplayName("SseEmitterRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SseEmitterRepositoryTest {

    private lateinit var sseEmitterRepository: SseEmitterRepository

    @BeforeEach
    fun setUp() {
        sseEmitterRepository = SseEmitterRepository()
    }

    @Test
    fun emitter를_저장한다() {
        val emitter = SseEmitter()

        val result = sseEmitterRepository.save(1L, emitter)

        assertThat(result).isSameAs(emitter)
        assertThat(sseEmitterRepository.findByUserId(1L)).containsExactly(emitter)
    }

    @Test
    fun 같은_사용자에_여러_emitter를_저장한다() {
        val emitter1 = SseEmitter()
        val emitter2 = SseEmitter()

        sseEmitterRepository.save(1L, emitter1)
        sseEmitterRepository.save(1L, emitter2)

        assertThat(sseEmitterRepository.findByUserId(1L)).containsExactly(emitter1, emitter2)
    }

    @Test
    fun 존재하지_않는_사용자_조회시_빈_목록을_반환한다() {
        val result = sseEmitterRepository.findByUserId(999L)

        assertThat(result).isEmpty()
    }

    @Test
    fun emitter를_제거한다() {
        val emitter1 = SseEmitter()
        val emitter2 = SseEmitter()
        sseEmitterRepository.save(1L, emitter1)
        sseEmitterRepository.save(1L, emitter2)

        sseEmitterRepository.remove(1L, emitter1)

        assertThat(sseEmitterRepository.findByUserId(1L)).containsExactly(emitter2)
    }

    @Test
    fun 마지막_emitter_제거시_사용자_항목을_삭제한다() {
        val emitter = SseEmitter()
        sseEmitterRepository.save(1L, emitter)

        sseEmitterRepository.remove(1L, emitter)

        assertThat(sseEmitterRepository.findByUserId(1L)).isEmpty()
    }
}
