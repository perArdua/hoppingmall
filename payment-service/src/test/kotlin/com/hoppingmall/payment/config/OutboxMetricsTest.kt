package com.hoppingmall.payment.config

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("OutboxMetrics 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OutboxMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var outboxMetrics: OutboxMetrics

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        outboxMetrics = OutboxMetrics(registry)
    }

    @Test
    fun Outbox_발행_성공_시_카운터가_증가한다() {
        outboxMetrics.recordOutboxPublished("payment-completed")

        assertThat(registry.counter("outbox.event.published.count").count()).isEqualTo(1.0)
    }

    @Test
    fun Outbox_발행_실패_시_카운터가_증가한다() {
        outboxMetrics.recordOutboxFailed("payment-completed")

        assertThat(registry.counter("outbox.event.failed.count").count()).isEqualTo(1.0)
    }

    @Test
    fun 토픽별_카운터가_태그와_함께_증가한다() {
        outboxMetrics.recordOutboxPublished("payment-completed")
        outboxMetrics.recordOutboxPublished("point-earn-request")

        assertThat(registry.counter("outbox.event.published.count").count()).isEqualTo(2.0)
    }
}
