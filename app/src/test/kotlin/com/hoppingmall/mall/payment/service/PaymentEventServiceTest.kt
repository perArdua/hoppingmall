package com.hoppingmall.mall.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.mall.support.fixture.failedFixture
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.successFixture
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import kotlin.test.assertEquals

class PaymentEventServiceTest {
    
    private val paymentEventPublisher: PaymentEventPublisher = mock()
    private val transactionalEventPublisher: TransactionalEventPublisher = mock()
    private val pointEarnRateStrategy: PointEarnRateStrategy = mock()
    private val objectMapper = ObjectMapper()
    private val paymentEventService = PaymentEventService(
        paymentEventPublisher,
        transactionalEventPublisher,
        pointEarnRateStrategy,
        objectMapper
    )
    
    @Test
    fun `결제 완료 이벤트를 발행한다`() {
        // given
        val payment = Payment.successFixture()
        
        // when
        paymentEventService.publishPaymentCompletedEvent(payment)
        
        // then
        verify(paymentEventPublisher).publishPaymentCompletedEvent(any())
    }
    
    @Test
    fun `포인트 적립 요청 이벤트를 발행한다`() {
        // given
        val payment = Payment.fixture()
        whenever(pointEarnRateStrategy.getEarnRate(payment.userId)).thenReturn(BigDecimal("0.01"))

        // when
        paymentEventService.publishPointEarnRequestEvent(payment)

        // then
        verify(paymentEventPublisher).publishPointEarnRequestEvent(any())
    }
    
    @Test
    fun `결제 완료 알림 이벤트를 발행한다`() {
        // given
        val payment = Payment.fixture()
        
        // when
        paymentEventService.publishPaymentCompletedNotification(payment)
        
        // then
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Payment"),
            aggregateId = eq("1"),
            eventType = eq("PaymentCompletedNotificationRequested"),
            eventData = any(),
            topic = eq("notification"),
            partitionKey = eq("1")
        )
    }
    
    @Test
    fun `포인트 적립 요청 이벤트의 금액이 정확히 계산된다`() {
        // given
        val payment = Payment.fixture(amount = BigDecimal("10000"))
        whenever(pointEarnRateStrategy.getEarnRate(payment.userId)).thenReturn(BigDecimal("0.01"))

        val captor = argumentCaptor<PointEarnRequestEvent>()

        // when
        paymentEventService.publishPointEarnRequestEvent(payment)

        // then
        verify(paymentEventPublisher).publishPointEarnRequestEvent(captor.capture())
        assertEquals(BigDecimal("100.00"), captor.firstValue.earnAmount)
        assertEquals("payment-1", captor.firstValue.eventId)
    }
    
    @Test
    fun `알림 이벤트의 내용이 정확히 설정된다`() {
        // given
        val payment = Payment.fixture()

        // when
        paymentEventService.publishPaymentCompletedNotification(payment)

        // then
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Payment"),
            aggregateId = eq("1"),
            eventType = eq("PaymentCompletedNotificationRequested"),
            eventData = argThat { eventData ->
                val data = eventData as Map<String, Any>
                val metadata = data["metadata"] as String
                val metadataMap = objectMapper.readValue(metadata, Map::class.java)
                data["eventId"] == "payment-1" &&
                    data["content"] == "주문번호 1의 결제가 성공적으로 완료되었습니다. 결제 금액: 50000원" &&
                    metadataMap["orderId"].toString() == "1" &&
                    metadataMap["paymentId"].toString() == "1"
            },
            topic = eq("notification"),
            partitionKey = eq("1")
        )
    }

    @Test
    fun `멤버십 업데이트 요청 이벤트를 발행한다`() {
        // given
        val payment = Payment.fixture()

        // when
        paymentEventService.publishMembershipUpdateEvent(payment)

        // then
        verify(paymentEventPublisher).publishMembershipUpdateEvent(any())
    }

    @Test
    fun `결제 실패 이벤트를 발행한다`() {
        // given
        val payment = Payment.failedFixture()

        // when
        paymentEventService.publishPaymentFailedEvent(payment)

        // then
        verify(paymentEventPublisher).publishPaymentFailedEvent(any())
    }

    @Test
    fun `결제 취소 이벤트를 발행한다`() {
        // given
        val payment = Payment.successFixture()

        // when
        paymentEventService.publishPaymentCancelledEvent(payment)

        // then
        verify(paymentEventPublisher).publishPaymentCancelledEvent(any())
    }

    @Test
    fun `결제 실패 알림 이벤트를 발행한다`() {
        // given
        val payment = Payment.failedFixture()

        // when
        paymentEventService.publishPaymentFailedNotification(payment)

        // then
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Payment"),
            aggregateId = eq("1"),
            eventType = eq("PaymentFailedNotificationRequested"),
            eventData = any(),
            topic = eq("notification"),
            partitionKey = eq("1")
        )
    }

    @Test
    fun `결제 취소 알림 이벤트를 발행한다`() {
        // given
        val payment = Payment.successFixture()

        // when
        paymentEventService.publishPaymentCancelledNotification(payment)

        // then
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Payment"),
            aggregateId = eq("1"),
            eventType = eq("PaymentCancelledNotificationRequested"),
            eventData = any(),
            topic = eq("notification"),
            partitionKey = eq("1")
        )
    }
} 
