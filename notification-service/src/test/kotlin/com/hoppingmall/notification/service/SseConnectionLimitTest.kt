package com.hoppingmall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@DisplayName("사용자별 SSE 연결 상한")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SseConnectionLimitTest {

    @Mock
    private lateinit var redisMessageListenerContainer: RedisMessageListenerContainer

    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }

    @Test
    fun 네번째_연결_뒤_연결_수는_3이고_첫_연결이_끝난다() {
        val repository = SseEmitterRepository()
        val service = NotificationSseService(repository, redisMessageListenerContainer, objectMapper)

        val first = service.connect(1L)
        val second = service.connect(1L)
        val third = service.connect(1L)
        val fourth = service.connect(1L)

        assertThat(repository.findByUserId(1L)).containsExactly(second, third, fourth)
        assertThat(isCompleted(first)).isTrue()
        assertThat(isCompleted(second)).isFalse()
        assertThat(isCompleted(third)).isFalse()
        assertThat(isCompleted(fourth)).isFalse()
    }

    @Test
    fun 상한을_넘을_때마다_그때_가장_오래된_연결을_끝낸다() {
        val repository = SseEmitterRepository()
        val emitters = (1..5).map { repository.save(1L, SseEmitter()) }

        assertThat(repository.findByUserId(1L)).containsExactly(emitters[2], emitters[3], emitters[4])
        assertThat(emitters.map { isCompleted(it) }).containsExactly(true, true, false, false, false)
    }

    @Test
    fun 다른_사용자의_연결은_상한에_세지_않는다() {
        val repository = SseEmitterRepository()
        val otherUserEmitters = (1..3).map { repository.save(2L, SseEmitter()) }

        (1..3).forEach { repository.save(1L, SseEmitter()) }

        assertThat(repository.findByUserId(1L)).hasSize(3)
        assertThat(repository.findByUserId(2L)).containsExactlyElementsOf(otherUserEmitters)
        assertThat(otherUserEmitters.none { isCompleted(it) }).isTrue()
    }

    @Test
    fun 연결이_끝나_목록에서_빠지면_새_연결은_기존_연결을_끝내지_않는다() {
        val repository = SseEmitterRepository()
        val first = repository.save(1L, SseEmitter())
        val second = repository.save(1L, SseEmitter())
        val third = repository.save(1L, SseEmitter())
        repository.remove(1L, first)

        val fourth = repository.save(1L, SseEmitter())

        assertThat(repository.findByUserId(1L)).containsExactly(second, third, fourth)
        assertThat(listOf(second, third, fourth).none { isCompleted(it) }).isTrue()
    }

    @Test
    fun 설정이_없으면_상한은_3이다() {
        ApplicationContextRunner()
            .withUserConfiguration(SseEmitterRepository::class.java)
            .run { context ->
                val repository = context.getBean(SseEmitterRepository::class.java)
                (1..4).forEach { repository.save(1L, SseEmitter()) }

                assertThat(repository.findByUserId(1L)).hasSize(3)
            }
    }

    @Test
    fun 상한은_설정값으로_바꿀_수_있다() {
        ApplicationContextRunner()
            .withUserConfiguration(SseEmitterRepository::class.java)
            .withPropertyValues("notification.sse.max-connections-per-user=2")
            .run { context ->
                val repository = context.getBean(SseEmitterRepository::class.java)
                val emitters = (1..3).map { repository.save(1L, SseEmitter()) }

                assertThat(repository.findByUserId(1L)).containsExactly(emitters[1], emitters[2])
                assertThat(isCompleted(emitters[0])).isTrue()
            }
    }

    @Test
    fun 상한이_1보다_작으면_만들_수_없다() {
        assertThatThrownBy { SseEmitterRepository(0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 같은_사용자가_동시에_연결해도_상한을_넘지_않고_밀려난_연결은_모두_끝난다() {
        val repository = SseEmitterRepository()
        val threadCount = 16
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            val futures = (1..threadCount).map {
                executor.submit(Callable {
                    val emitter = SseEmitter()
                    ready.countDown()
                    start.await()
                    repository.save(1L, emitter)
                })
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val saved = futures.map { it.get(10, TimeUnit.SECONDS) }

            val remaining = repository.findByUserId(1L)
            assertThat(remaining).hasSize(3)
            assertThat(remaining.none { isCompleted(it) }).isTrue()
            assertThat(saved.filterNot { it in remaining }.all { isCompleted(it) }).isTrue()
        } finally {
            executor.shutdownNow()
        }
    }

    private fun isCompleted(emitter: SseEmitter): Boolean =
        runCatching { emitter.send("ping") }.exceptionOrNull() is IllegalStateException
}
