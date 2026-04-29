package com.hoppingmall.payment.coupon.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("CouponCompensationMetrics")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponCompensationMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = CouponCompensationMetrics(registry)

    @Test
    fun 동기_보상_성공_카운터를_증가시킨다() {
        metrics.recordSyncSuccess()
        metrics.recordSyncSuccess()

        assertThat(registry.counter("coupon.compensation.sync.success").count()).isEqualTo(2.0)
    }

    @Test
    fun 동기_보상_실패_카운터를_증가시킨다() {
        metrics.recordSyncFailure()

        assertThat(registry.counter("coupon.compensation.sync.failure").count()).isEqualTo(1.0)
    }

    @Test
    fun 비동기_발행_카운터를_증가시킨다() {
        metrics.recordAsyncPublished()

        assertThat(registry.counter("coupon.compensation.async.published").count()).isEqualTo(1.0)
    }

    @Test
    fun 비동기_처리_카운터를_증가시킨다() {
        metrics.recordAsyncConsumed()

        assertThat(registry.counter("coupon.compensation.async.consumed").count()).isEqualTo(1.0)
    }

    @Test
    fun DLQ_카운터를_증가시킨다() {
        metrics.recordDlq()

        assertThat(registry.counter("coupon.compensation.dlq").count()).isEqualTo(1.0)
    }
}
