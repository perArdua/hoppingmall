package com.hoppingmall.outbox.metrics

import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@DisplayName("OutboxMetrics")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OutboxMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var outboxMetrics: OutboxMetrics

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        outboxEventRepository = mock()
        whenever(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(3L)
        whenever(outboxEventRepository.countByStatus(OutboxStatus.RETRYING)).thenReturn(2L)
        whenever(outboxEventRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(1L)
        outboxMetrics = OutboxMetrics(registry, outboxEventRepository)
    }

    @Test
    fun outbox_발행_성공_시_카운터가_증가한다() {
        outboxMetrics.recordOutboxPublished("order-saga")

        assertThat(registry.counter("outbox.event.published.count").count()).isEqualTo(1.0)
    }

    @Test
    fun 현재_상태_gauge가_초기화된다() {
        assertThat(registry.get("outbox.event.pending.count").gauge().value()).isEqualTo(3.0)
        assertThat(registry.get("outbox.event.retrying.count").gauge().value()).isEqualTo(2.0)
        assertThat(registry.get("outbox.event.failed.current").gauge().value()).isEqualTo(1.0)
    }

    @Test
    fun publish_latency가_기록된다() {
        outboxMetrics.recordPublishLatency(LocalDateTime.now().minusNanos(50_000_000))

        assertThat(registry.get("outbox.event.publish.latency").timer().count()).isEqualTo(1L)
    }

    @Test
    fun outbox_발행_실패_시_카운터가_증가한다() {
        outboxMetrics.recordOutboxFailed("payment")

        assertThat(registry.counter("outbox.event.failed.count").count()).isEqualTo(1.0)
    }

    @Test
    fun 토픽별_발행_성공_카운터가_증가한다() {
        outboxMetrics.recordOutboxPublished("payment")
        outboxMetrics.recordOutboxPublished("payment")
        outboxMetrics.recordOutboxPublished("order-events")

        assertThat(
            registry.counter("outbox.event.published.by_topic", "topic", "payment").count()
        ).isEqualTo(2.0)
        assertThat(
            registry.counter("outbox.event.published.by_topic", "topic", "order-events").count()
        ).isEqualTo(1.0)
    }

    @Test
    fun 토픽별_발행_실패_카운터가_증가한다() {
        outboxMetrics.recordOutboxFailed("payment")
        outboxMetrics.recordOutboxFailed("payment")

        assertThat(
            registry.counter("outbox.event.failed.by_topic", "topic", "payment").count()
        ).isEqualTo(2.0)
    }

    @Test
    fun 전체_발행_성공_카운터가_누적된다() {
        outboxMetrics.recordOutboxPublished("payment")
        outboxMetrics.recordOutboxPublished("order-events")

        assertThat(registry.counter("outbox.event.published.count").count()).isEqualTo(2.0)
    }
}
