package com.hoppingmall.payment.config

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("PaymentMetrics 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var paymentMetrics: PaymentMetrics

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        paymentMetrics = PaymentMetrics(registry)
    }

    @Test
    fun 결제_성공_시_카운터가_증가한다() {
        paymentMetrics.recordPaymentCompleted(BigDecimal("50000"), "CREDIT_CARD")

        assertThat(registry.counter("payment.completed.count").count()).isEqualTo(1.0)
        assertThat(registry.counter("payment.amount.total").count()).isEqualTo(50000.0)
    }

    @Test
    fun 결제_실패_시_카운터가_증가한다() {
        paymentMetrics.recordPaymentFailed()

        assertThat(registry.counter("payment.failed.count").count()).isEqualTo(1.0)
    }

    @Test
    fun 결제_취소_시_카운터가_증가한다() {
        paymentMetrics.recordPaymentCancelled()

        assertThat(registry.counter("payment.cancelled.count").count()).isEqualTo(1.0)
    }

    @Test
    fun 결제_처리_시간을_기록한다() {
        val result = paymentMetrics.recordProcessingTime { "done" }

        assertThat(result).isEqualTo("done")
        assertThat(registry.timer("payment.processing.duration").count()).isEqualTo(1)
    }

    @Test
    fun 결제_수단별_카운터가_태그와_함께_증가한다() {
        paymentMetrics.recordPaymentCompleted(BigDecimal("10000"), "CREDIT_CARD")
        paymentMetrics.recordPaymentCompleted(BigDecimal("20000"), "BANK_TRANSFER")

        assertThat(registry.counter("payment.completed.count").count()).isEqualTo(2.0)
        assertThat(registry.counter("payment.amount.total").count()).isEqualTo(30000.0)
    }
}
