package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.hoppingmall.product.product.domain.StatisticsEventLog
import com.hoppingmall.product.product.domain.repository.StatisticsEventLogRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.port.OrderItemInfo
import com.hoppingmall.product.statistics.port.OrderItemQueryPort
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("StatisticsEventConsumer")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class StatisticsEventConsumerTest {

    @Mock
    private lateinit var statisticsEventLogRepository: StatisticsEventLogRepository

    @Mock
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @Mock
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @Spy
    private var objectMapper: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())

    @InjectMocks
    private lateinit var consumer: StatisticsEventConsumer

    @Test
    fun 결제_완료_이벤트를_처리한다() {
        val message = """{"paymentId":1,"orderId":10,"userId":1,"amount":10000,"pointAmount":0,"transactionId":"txn-1","completedAt":"2026-01-01T00:00:00"}"""
        val items = listOf(
            OrderItemInfo(id = 1L, orderId = 10L, productId = 1L, quantity = 2, totalPrice = BigDecimal("20000"))
        )

        whenever(statisticsEventLogRepository.existsByEventId("txn-1")).thenReturn(false)
        whenever(orderItemQueryPort.findByOrderId(10L)).thenReturn(items)
        whenever(statisticsEventLogRepository.save(any<StatisticsEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentCompleted(message)

        verify(productStatisticsCommandService).incrementSalesStats(1L, 2L, BigDecimal("20000"))
    }

    @Test
    fun 이미_처리된_결제_완료_이벤트를_무시한다() {
        val message = """{"paymentId":1,"orderId":10,"userId":1,"amount":10000,"pointAmount":0,"transactionId":"txn-1","completedAt":"2026-01-01T00:00:00"}"""

        whenever(statisticsEventLogRepository.existsByEventId("txn-1")).thenReturn(true)

        consumer.handlePaymentCompleted(message)

        verify(productStatisticsCommandService, never()).incrementSalesStats(any(), any(), any())
    }

    @Test
    fun 결제_취소_보상_이벤트를_처리한다() {
        val message = """{"eventType":"PaymentCancelled","eventId":"evt-1","paymentId":1,"orderId":10,"userId":1,"amount":10000,"transactionId":"txn-1"}"""
        val items = listOf(
            OrderItemInfo(id = 1L, orderId = 10L, productId = 1L, quantity = 2, totalPrice = BigDecimal("20000"))
        )

        whenever(statisticsEventLogRepository.existsByEventId("evt-1")).thenReturn(false)
        whenever(orderItemQueryPort.findByOrderId(10L)).thenReturn(items)
        whenever(statisticsEventLogRepository.save(any<StatisticsEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handleCompensationEvent(message)

        verify(productStatisticsCommandService).decrementSalesStats(1L, 2L, BigDecimal("20000"))
    }

    @Test
    fun 이미_처리된_결제_취소_보상_이벤트를_무시한다() {
        val message = """{"eventType":"PaymentCancelled","eventId":"evt-1","paymentId":1,"orderId":10,"userId":1,"amount":10000,"transactionId":"txn-1"}"""

        whenever(statisticsEventLogRepository.existsByEventId("evt-1")).thenReturn(true)

        consumer.handleCompensationEvent(message)

        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }

    @Test
    fun PaymentCancelled가_아닌_보상_이벤트는_무시한다() {
        val message = """{"eventType":"Other","eventId":"evt-1","orderId":10}"""

        consumer.handleCompensationEvent(message)

        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }

    @Test
    fun 결제_역보상_이벤트를_처리한다() {
        val message = """{"eventType":"PaymentReversalRequested","eventId":"rev-1","orderId":10}"""
        val items = listOf(
            OrderItemInfo(id = 1L, orderId = 10L, productId = 1L, quantity = 1, totalPrice = BigDecimal("10000"))
        )

        whenever(statisticsEventLogRepository.existsByEventId("rev-1")).thenReturn(false)
        whenever(orderItemQueryPort.findByOrderId(10L)).thenReturn(items)
        whenever(statisticsEventLogRepository.save(any<StatisticsEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentReversal(message)

        verify(productStatisticsCommandService).decrementSalesStats(1L, 1L, BigDecimal("10000"))
    }

    @Test
    fun 이미_처리된_역보상_이벤트를_무시한다() {
        val message = """{"eventType":"PaymentReversalRequested","eventId":"rev-1","orderId":10}"""

        whenever(statisticsEventLogRepository.existsByEventId("rev-1")).thenReturn(true)

        consumer.handlePaymentReversal(message)

        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }

    @Test
    fun PaymentReversalRequested가_아닌_역보상_이벤트는_무시한다() {
        val message = """{"eventType":"Other","eventId":"rev-1","orderId":10}"""

        consumer.handlePaymentReversal(message)

        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }

    @Test
    fun eventId가_없는_역보상_이벤트는_무시한다() {
        val message = """{"eventType":"PaymentReversalRequested","orderId":10}"""

        consumer.handlePaymentReversal(message)

        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }

    @Test
    fun orderId가_없는_역보상_이벤트는_무시한다() {
        val message = """{"eventType":"PaymentReversalRequested","eventId":"rev-1"}"""

        consumer.handlePaymentReversal(message)

        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }
}
