package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.point.service.PointPolicyService
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.successFixture
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.point.dto.response.PointPolicyResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals

class PaymentEventServiceTest {
    
    private val paymentEventPublisher: PaymentEventPublisher = mock()
    private val transactionalEventPublisher: TransactionalEventPublisher = mock()
    private val pointPolicyService: PointPolicyService = mock()
    private val paymentEventService = PaymentEventService(paymentEventPublisher, transactionalEventPublisher, pointPolicyService)
    
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
        whenever(pointPolicyService.getCurrentPolicy()).thenReturn(
            PointPolicyResponse(
                id = 1L,
                policyName = "기본 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.05"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("100000"),
                isActive = true,
                description = "1% 적립"
            )
        )
        
        val captor = argumentCaptor<PointEarnRequestEvent>()
        
        // when
        paymentEventService.publishPointEarnRequestEvent(payment)
        
        // then
        verify(paymentEventPublisher).publishPointEarnRequestEvent(captor.capture())
        assertEquals(BigDecimal("100.00"), captor.firstValue.earnAmount)
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
                data["content"] == "주문번호 1의 결제가 성공적으로 완료되었습니다. 결제 금액: 50000원"
            },
            topic = eq("notification"),
            partitionKey = eq("1")
        )
    }
} 