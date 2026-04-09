package com.hoppingmall.order.refund.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("RefundEventLog")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundEventLogTest {

    @Test
    fun 환불_이벤트_로그를_생성한다() {
        val log = RefundEventLog(
            eventId = "evt-refund-1",
            eventType = "REFUND_COMPLETED",
            refundId = 1L,
            orderId = 10L
        )

        assertThat(log.eventId).isEqualTo("evt-refund-1")
        assertThat(log.eventType).isEqualTo("REFUND_COMPLETED")
        assertThat(log.refundId).isEqualTo(1L)
        assertThat(log.orderId).isEqualTo(10L)
    }
}
