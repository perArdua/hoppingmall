package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate

@DisplayName("NotificationEventConsumer 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationEventConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun 알림_이벤트를_수신하여_DB에_저장한다() {
        val event = NotificationEvent(
            eventId = "notif-integration-001",
            userId = 1L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제 완료",
            content = "주문번호 100의 결제가 완료되었습니다",
            metadata = """{"orderId": 100}"""
        )

        kafkaTemplate.send("notification", event.userId.toString(), event)

        awaitUntil {
            notificationRepository.existsByEventId("notif-integration-001")
        }

        val saved = notificationRepository.findByUserId(1L)
        assertThat(saved).hasSize(1)
        assertThat(saved[0].eventId).isEqualTo("notif-integration-001")
        assertThat(saved[0].type).isEqualTo(NotificationType.PAYMENT_COMPLETED)
        assertThat(saved[0].title).isEqualTo("결제 완료")
        assertThat(saved[0].content).isEqualTo("주문번호 100의 결제가 완료되었습니다")
    }

    @Test
    fun 중복_이벤트는_한_번만_저장된다() {
        val event = NotificationEvent(
            eventId = "notif-dedup-001",
            userId = 2L,
            type = NotificationType.POINT_EARNED,
            title = "포인트 적립",
            content = "100포인트가 적립되었습니다"
        )

        kafkaTemplate.send("notification", event.userId.toString(), event)
        kafkaTemplate.send("notification", event.userId.toString(), event)

        awaitUntil {
            notificationRepository.existsByEventId("notif-dedup-001")
        }
        Thread.sleep(1000)

        val saved = notificationRepository.findByUserId(2L)
        assertThat(saved).hasSize(1)
    }
}
