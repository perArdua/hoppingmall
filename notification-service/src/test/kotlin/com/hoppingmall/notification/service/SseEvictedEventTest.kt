package com.hoppingmall.notification.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@DisplayName("SSE 연결 상한으로 밀려나는 연결의 evicted 이벤트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SseEvictedEventTest {

    @Test
    fun 밀려나는_연결은_끝나기_전에_evicted_이벤트를_한_번_받는다() {
        val repository = SseEmitterRepository()
        val first = RecordingEmitter()

        repository.save(1L, first)
        (1..3).forEach { repository.save(1L, RecordingEmitter()) }

        assertThat(first.log).containsExactly(EVICTED, COMPLETE)
    }

    @Test
    fun 밀려나지_않은_연결은_evicted_이벤트를_받지_않는다() {
        val repository = SseEmitterRepository()
        val emitters = (1..4).map { RecordingEmitter() }

        emitters.forEach { repository.save(1L, it) }

        emitters.drop(1).forEach { assertThat(it.log).isEmpty() }
    }

    @Test
    fun 상한을_넘을_때마다_밀려나는_연결마다_evicted_이벤트를_보낸다() {
        val repository = SseEmitterRepository(2)
        val emitters = (1..4).map { RecordingEmitter() }

        emitters.forEach { repository.save(1L, it) }

        assertThat(emitters[0].log).containsExactly(EVICTED, COMPLETE)
        assertThat(emitters[1].log).containsExactly(EVICTED, COMPLETE)
        assertThat(emitters[2].log).isEmpty()
        assertThat(emitters[3].log).isEmpty()
    }

    @Test
    fun 다른_사용자의_연결에는_evicted_이벤트를_보내지_않는다() {
        val repository = SseEmitterRepository()
        val otherUser = RecordingEmitter()
        repository.save(2L, otherUser)

        (1..4).forEach { repository.save(1L, RecordingEmitter()) }

        assertThat(otherUser.log).isEmpty()
    }

    @Test
    fun evicted_이벤트_전송이_실패해도_밀려난_연결은_끝나고_새_연결은_저장된다() {
        val repository = SseEmitterRepository()
        val first = RecordingEmitter(failOnSend = true)
        repository.save(1L, first)
        repository.save(1L, RecordingEmitter())
        repository.save(1L, RecordingEmitter())
        val fourth = RecordingEmitter()

        val saved = repository.save(1L, fourth)

        assertThat(saved).isSameAs(fourth)
        assertThat(repository.findByUserId(1L)).doesNotContain(first).contains(fourth)
        assertThat(first.log).containsExactly(COMPLETE)
    }

    @Test
    fun 이미_끝난_연결이_밀려나도_새_연결_저장은_실패하지_않는다() {
        val repository = SseEmitterRepository()
        val first = SseEmitter()
        repository.save(1L, first)
        first.complete()
        repository.save(1L, SseEmitter())
        repository.save(1L, SseEmitter())
        val fourth = SseEmitter()

        assertThatCode { repository.save(1L, fourth) }.doesNotThrowAnyException()
        assertThat(repository.findByUserId(1L)).hasSize(3).contains(fourth).doesNotContain(first)
    }

    private class RecordingEmitter(
        private val failOnSend: Boolean = false
    ) : SseEmitter() {

        val log = CopyOnWriteArrayList<String>()

        override fun send(builder: SseEmitter.SseEventBuilder) {
            if (failOnSend) throw IOException("연결 끊김")
            val text = builder.build().joinToString("") { it.data.toString() }
            log.add(text.lineSequence().firstOrNull { it.startsWith("event:") } ?: text)
            super.send(builder)
        }

        override fun complete() {
            log.add(COMPLETE)
            super.complete()
        }
    }

    companion object {
        private const val EVICTED = "event:evicted"
        private const val COMPLETE = "complete"
    }
}
