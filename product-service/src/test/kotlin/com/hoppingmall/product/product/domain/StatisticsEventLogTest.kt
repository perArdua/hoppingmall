package com.hoppingmall.product.product.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("StatisticsEventLog 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class StatisticsEventLogTest {

    @Test
    fun 통계_이벤트_로그를_생성한다() {
        val log = StatisticsEventLog(
            eventId = "evt-123", eventType = "PaymentCompleted", orderId = 1L
        )

        assertThat(log.eventId).isEqualTo("evt-123")
        assertThat(log.eventType).isEqualTo("PaymentCompleted")
        assertThat(log.orderId).isEqualTo(1L)
    }
}
