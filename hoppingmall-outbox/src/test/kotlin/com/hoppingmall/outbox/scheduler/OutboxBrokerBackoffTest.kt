package com.hoppingmall.outbox.scheduler

import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

@DisplayName("OutboxBrokerBackoff")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OutboxBrokerBackoffTest {

    @Mock
    private lateinit var outboxMetrics: OutboxMetrics

    private val clock = MutableClock(Instant.parse("2026-11-25T00:00:00Z"))

    private fun backoff(jitter: Double = 0.0, initialMs: Long = 1_000, maxMs: Long = 30_000) =
        OutboxBrokerBackoff(outboxMetrics, initialMs, maxMs, clock, FixedRandom(jitter))

    @Test
    fun 브로커_실패가_이어지면_1초_2초_4초로_늘고_최대값에서_멈춘다() {
        val backoff = backoff()

        val delays = (1..8).map { backoff.recordFailure() }

        assertThat(delays).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L)
        verify(outboxMetrics, times(8)).recordBrokerFailure()
    }

    @Test
    fun 지터는_기준_대기의_80퍼센트_이상이고_기준을_넘지_않는다() {
        val lowest = backoff(jitter = 0.999_999)
        val highest = backoff(jitter = 0.0)

        val lowDelays = (1..7).map { lowest.recordFailure() }
        val highDelays = (1..7).map { highest.recordFailure() }

        listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L).forEachIndexed { i, base ->
            assertThat(lowDelays[i]).isGreaterThanOrEqualTo(base * 8 / 10).isLessThanOrEqualTo(base)
            assertThat(highDelays[i]).isEqualTo(base)
        }
        assertThat(lowDelays).allMatch { it <= 30_000L }
    }

    @Test
    fun 실제_난수로도_대기는_기준의_80퍼센트와_기준_사이에_있다() {
        val backoff = OutboxBrokerBackoff(outboxMetrics, 1_000, 30_000, clock)

        val delays = (1..200).map { backoff.recordFailure() }

        assertThat(delays.take(5).zip(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)))
            .allMatch { (delay, base) -> delay in (base * 8 / 10)..base }
        assertThat(delays.drop(5)).allMatch { it in 24_000L..30_000L }
    }

    @Test
    fun 대기_시간_동안만_백오프_상태다() {
        val backoff = backoff()
        assertThat(backoff.isBackingOff()).isFalse()

        backoff.recordFailure()
        assertThat(backoff.isBackingOff()).isTrue()

        clock.advanceMillis(999)
        assertThat(backoff.isBackingOff()).isTrue()

        clock.advanceMillis(1)
        assertThat(backoff.isBackingOff()).isFalse()
    }

    @Test
    fun 대기가_끝나도_성공_전까지는_다음_실패에서_단계가_이어진다() {
        val backoff = backoff()
        backoff.recordFailure()
        clock.advanceMillis(1_000)

        assertThat(backoff.isBackingOff()).isFalse()
        assertThat(backoff.recordFailure()).isEqualTo(2_000L)
    }

    @Test
    fun 한_번_성공하면_대기와_단계가_처음으로_돌아간다() {
        val backoff = backoff()
        repeat(4) { backoff.recordFailure() }

        backoff.recordSuccess()

        assertThat(backoff.isBackingOff()).isFalse()
        assertThat(backoff.recordFailure()).isEqualTo(1_000L)
    }

    @Test
    fun 설정한_처음_대기와_최대_대기를_쓴다() {
        val backoff = backoff(initialMs = 500, maxMs = 3_000)

        val delays = (1..5).map { backoff.recordFailure() }

        assertThat(delays).containsExactly(500L, 1_000L, 2_000L, 3_000L, 3_000L)
    }

    @Test
    fun 실패가_아주_많이_이어져도_대기가_넘치지_않고_최대값이다() {
        val backoff = backoff()

        val last = (1..100).map { backoff.recordFailure() }.last()

        assertThat(last).isEqualTo(30_000L)
    }

    @Test
    fun 생성하면_백오프_상태를_지표에_묶는다() {
        backoff()

        verify(outboxMetrics).bindBrokerBackoff(any())
    }

    @Test
    fun 백오프_지표는_대기_중이면_1이고_풀리면_0이다() {
        val registry = SimpleMeterRegistry()
        val metrics = OutboxMetrics(registry, mock<OutboxEventRepository>())
        val backoff = OutboxBrokerBackoff(metrics, 1_000, 30_000, clock, FixedRandom(0.0))
        val gauge = registry.get("outbox.broker.backoff.active").gauge()

        assertThat(gauge.value()).isEqualTo(0.0)
        backoff.recordFailure()
        assertThat(gauge.value()).isEqualTo(1.0)
        assertThat(registry.counter("outbox.broker.failure.count").count()).isEqualTo(1.0)
        backoff.recordSuccess()
        assertThat(gauge.value()).isEqualTo(0.0)
    }

    private class FixedRandom(private val value: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advanceMillis(millis: Long) {
            now = now.plusMillis(millis)
        }
    }
}
