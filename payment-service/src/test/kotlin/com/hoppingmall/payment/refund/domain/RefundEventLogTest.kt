package com.hoppingmall.payment.refund.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("RefundEventLog")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class RefundEventLogTest {

    private fun createRefundEventLog(
        eventId: String = "event-001",
        eventType: String = "REFUND_COMPLETED",
        refundId: Long = 1L,
        orderId: Long = 100L
    ): RefundEventLog {
        return RefundEventLog(
            eventId = eventId,
            eventType = eventType,
            refundId = refundId,
            orderId = orderId
        )
    }

    @Test
    fun markStepCompleted_완료된_단계를_추가한다() {
        val log = createRefundEventLog()

        log.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)

        assertThat(log.completedSteps).contains(RefundEventLog.PAYMENT_UPDATED)
    }

    @Test
    fun markStepCompleted_여러_단계를_추가할_수_있다() {
        val log = createRefundEventLog()

        log.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
        log.markStepCompleted(RefundEventLog.POINTS_REFUNDED)
        log.markStepCompleted(RefundEventLog.COUPON_RESTORED)

        assertThat(log.completedSteps).containsExactlyInAnyOrder(
            RefundEventLog.PAYMENT_UPDATED,
            RefundEventLog.POINTS_REFUNDED,
            RefundEventLog.COUPON_RESTORED
        )
    }

    @Test
    fun isStepCompleted_완료된_단계면_true를_반환한다() {
        val log = createRefundEventLog()
        log.markStepCompleted(RefundEventLog.INVENTORY_RESTORED)

        assertThat(log.isStepCompleted(RefundEventLog.INVENTORY_RESTORED)).isTrue()
    }

    @Test
    fun isStepCompleted_완료되지_않은_단계면_false를_반환한다() {
        val log = createRefundEventLog()

        assertThat(log.isStepCompleted(RefundEventLog.ORDER_CANCELLED)).isFalse()
    }

    @Test
    fun isStepCompleted_모든_상수_단계를_검증한다() {
        val log = createRefundEventLog()
        log.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
        log.markStepCompleted(RefundEventLog.POINTS_REFUNDED)
        log.markStepCompleted(RefundEventLog.COUPON_RESTORED)
        log.markStepCompleted(RefundEventLog.INVENTORY_RESTORED)
        log.markStepCompleted(RefundEventLog.STATS_UPDATED)
        log.markStepCompleted(RefundEventLog.ORDER_CANCELLED)

        assertThat(log.isStepCompleted(RefundEventLog.PAYMENT_UPDATED)).isTrue()
        assertThat(log.isStepCompleted(RefundEventLog.POINTS_REFUNDED)).isTrue()
        assertThat(log.isStepCompleted(RefundEventLog.COUPON_RESTORED)).isTrue()
        assertThat(log.isStepCompleted(RefundEventLog.INVENTORY_RESTORED)).isTrue()
        assertThat(log.isStepCompleted(RefundEventLog.STATS_UPDATED)).isTrue()
        assertThat(log.isStepCompleted(RefundEventLog.ORDER_CANCELLED)).isTrue()
    }

    @Test
    fun 동일한_단계를_중복_추가해도_한_번만_저장된다() {
        val log = createRefundEventLog()

        log.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
        log.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)

        assertThat(log.completedSteps).hasSize(1)
    }
}
