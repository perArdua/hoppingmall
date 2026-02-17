package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal

@DisplayName("PointEventConsumer 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointEventConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @MockBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Test
    fun 포인트_적립_이벤트를_수신하여_포인트를_적립한다() {
        val event = PointEarnRequestEvent(
            eventId = "point-integration-001",
            userId = 10L,
            orderId = 100L,
            paymentId = 200L,
            earnAmount = BigDecimal("500"),
            reason = "결제 완료"
        )

        kafkaTemplate.send("point-earn-request", event.userId.toString(), event)

        awaitUntil {
            pointHistoryRepository.existsByEventId("point-integration-001")
        }

        val point = pointRepository.findByUserId(10L)
        assertThat(point).isNotNull
        assertThat(point!!.balance).isEqualByComparingTo(BigDecimal("500"))

        val history = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(10L)
        assertThat(history).hasSize(1)
        assertThat(history[0].type).isEqualTo(PointType.EARN)
        assertThat(history[0].amount).isEqualByComparingTo(BigDecimal("500"))
        assertThat(history[0].eventId).isEqualTo("point-integration-001")
    }

    @Test
    fun 중복_포인트_적립_이벤트는_무시된다() {
        val event = PointEarnRequestEvent(
            eventId = "point-dedup-001",
            userId = 11L,
            orderId = 101L,
            paymentId = 201L,
            earnAmount = BigDecimal("300"),
            reason = "결제 완료"
        )

        kafkaTemplate.send("point-earn-request", event.userId.toString(), event)

        awaitUntil {
            pointHistoryRepository.existsByEventId("point-dedup-001")
        }

        kafkaTemplate.send("point-earn-request", event.userId.toString(), event)
        Thread.sleep(1000)

        val point = pointRepository.findByUserId(11L)
        assertThat(point).isNotNull
        assertThat(point!!.balance).isEqualByComparingTo(BigDecimal("300"))

        val histories = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(11L)
        assertThat(histories).hasSize(1)
    }

    @Test
    fun 알림_발행이_호출된다() {
        val event = PointEarnRequestEvent(
            eventId = "point-notif-001",
            userId = 12L,
            orderId = 102L,
            paymentId = 202L,
            earnAmount = BigDecimal("100"),
            reason = "결제 완료"
        )

        kafkaTemplate.send("point-earn-request", event.userId.toString(), event)

        awaitUntil {
            pointHistoryRepository.existsByEventId("point-notif-001")
        }

        verify(transactionalEventPublisher, atLeastOnce()).publishEvent(
            aggregateType = any(),
            aggregateId = any(),
            eventType = any(),
            eventData = any(),
            topic = any(),
            partitionKey = any()
        )
    }
}
