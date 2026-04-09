package com.hoppingmall.order.order.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("SagaEventLog")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SagaEventLogTest {

    @Test
    fun 스텝_완료를_기록한다() {
        val log = SagaEventLog(
            eventId = "evt-1",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        )

        log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)

        assertThat(log.isStepCompleted(SagaEventLog.LOCAL_COMPLETED)).isTrue()
        assertThat(log.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isFalse()
    }

    @Test
    fun 모든_스텝이_완료되면_isFullyCompleted가_true이다() {
        val log = SagaEventLog(
            eventId = "evt-2",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        )

        log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        log.markStepCompleted(SagaEventLog.REMOTE_COMPLETED)

        assertThat(log.isFullyCompleted()).isTrue()
    }

    @Test
    fun 일부_스텝만_완료되면_isFullyCompleted가_false이다() {
        val log = SagaEventLog(
            eventId = "evt-3",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        )

        log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)

        assertThat(log.isFullyCompleted()).isFalse()
    }

    @Test
    fun 스텝이_없으면_isFullyCompleted가_false이다() {
        val log = SagaEventLog(
            eventId = "evt-4",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        )

        assertThat(log.isFullyCompleted()).isFalse()
    }

    @Test
    fun 중복_스텝_추가를_무시한다() {
        val log = SagaEventLog(
            eventId = "evt-5",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        )

        log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)

        assertThat(log.completedSteps).hasSize(1)
    }

    @Test
    fun 생성_시_기본_필드가_초기화된다() {
        val log = SagaEventLog(
            eventId = "evt-6",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        )

        assertThat(log.id).isNull()
        assertThat(log.createdAt).isNotNull()
        assertThat(log.completedSteps).isEmpty()
        assertThat(log.eventId).isEqualTo("evt-6")
        assertThat(log.eventType).isEqualTo("PAYMENT_COMPLETED")
        assertThat(log.orderId).isEqualTo(1L)
    }

    @Test
    fun 상수_값이_올바르다() {
        assertThat(SagaEventLog.LOCAL_COMPLETED).isEqualTo("LOCAL_COMPLETED")
        assertThat(SagaEventLog.REMOTE_COMPLETED).isEqualTo("REMOTE_COMPLETED")
    }
}
