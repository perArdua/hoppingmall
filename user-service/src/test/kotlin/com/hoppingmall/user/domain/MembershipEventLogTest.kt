package com.hoppingmall.user.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("MembershipEventLog")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipEventLogTest {

    @Test
    fun 멤버십_이벤트_로그를_생성한다() {
        val log = MembershipEventLog(
            eventId = "evt-001",
            paymentId = 100L,
            orderId = 200L
        )

        assertThat(log.eventId).isEqualTo("evt-001")
        assertThat(log.paymentId).isEqualTo(100L)
        assertThat(log.orderId).isEqualTo(200L)
    }
}
