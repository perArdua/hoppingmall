package com.hoppingmall.mall.payment.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("CompensationEventLog")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CompensationEventLogTest {

    @Test
    fun `보상_이벤트_로그_생성`() {
        // given & when
        val log = CompensationEventLog(
            eventId = "payment-failed-1",
            compensationType = "PAYMENT_FAILED",
            paymentId = 1L,
            orderId = 1L
        )

        // then
        assertEquals("payment-failed-1", log.eventId)
        assertEquals("PAYMENT_FAILED", log.compensationType)
        assertEquals(1L, log.paymentId)
        assertEquals(1L, log.orderId)
    }
}
