package com.hoppingmall.mall.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
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

    @Nested
    @DisplayName("save")
    inner class Save {

        @Test
        fun Emitter를_저장한다() {
            val emitter = SseEmitter()

            val result = sseEmitterRepository.save(1L, emitter)

            assertEquals(emitter, result)
            assertEquals(1, sseEmitterRepository.findByUserId(1L).size)
        }

        @Test
        fun 같은_사용자에_여러_Emitter를_저장한다() {
            val emitter1 = SseEmitter()
            val emitter2 = SseEmitter()

            sseEmitterRepository.save(1L, emitter1)
            sseEmitterRepository.save(1L, emitter2)

            assertEquals(2, sseEmitterRepository.findByUserId(1L).size)
        }
    }

    @Nested
    @DisplayName("findByUserId")
    inner class FindByUserId {

        @Test
        fun 사용자의_Emitter_목록을_조회한다() {
            val emitter = SseEmitter()
            sseEmitterRepository.save(1L, emitter)

            val result = sseEmitterRepository.findByUserId(1L)

            assertEquals(1, result.size)
            assertEquals(emitter, result[0])
        }

        @Test
        fun 존재하지_않는_사용자는_빈_목록을_반환한다() {
            val result = sseEmitterRepository.findByUserId(999L)

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("remove")
    inner class Remove {

        @Test
        fun Emitter를_제거한다() {
            val emitter = SseEmitter()
            sseEmitterRepository.save(1L, emitter)

            sseEmitterRepository.remove(1L, emitter)

            assertTrue(sseEmitterRepository.findByUserId(1L).isEmpty())
        }

        @Test
        fun 여러_Emitter_중_하나만_제거한다() {
            val emitter1 = SseEmitter()
            val emitter2 = SseEmitter()
            sseEmitterRepository.save(1L, emitter1)
            sseEmitterRepository.save(1L, emitter2)

            sseEmitterRepository.remove(1L, emitter1)

            val remaining = sseEmitterRepository.findByUserId(1L)
            assertEquals(1, remaining.size)
            assertEquals(emitter2, remaining[0])
        }
    }
}
