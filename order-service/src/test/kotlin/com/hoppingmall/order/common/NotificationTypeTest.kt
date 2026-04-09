package com.hoppingmall.order.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("NotificationType")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationTypeTest {

    @Test
    fun 모든_알림_타입이_존재한다() {
        val types = NotificationType.entries

        assertThat(types).containsExactlyInAnyOrder(
            NotificationType.PAYMENT_COMPLETED,
            NotificationType.PAYMENT_FAILED,
            NotificationType.PAYMENT_CANCELLED,
            NotificationType.POINT_EARNED,
            NotificationType.SHIPPING_STARTED,
            NotificationType.SHIPPING_DELIVERED
        )
    }

    @Test
    fun 알림_타입의_name이_올바르다() {
        assertThat(NotificationType.SHIPPING_STARTED.name).isEqualTo("SHIPPING_STARTED")
        assertThat(NotificationType.SHIPPING_DELIVERED.name).isEqualTo("SHIPPING_DELIVERED")
    }

    @Test
    fun valueOf로_알림_타입을_조회한다() {
        assertThat(NotificationType.valueOf("PAYMENT_COMPLETED"))
            .isEqualTo(NotificationType.PAYMENT_COMPLETED)
    }
}
