package com.hoppingmall.order.order.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 취소 실패 처리의 CANCEL_FAILED 전환 로그")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCancellationFailedLogTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    private val logger = LoggerFactory.getLogger(OrderCancellationResultConsumer::class.java) as Logger

    private val appender = ListAppender<ILoggingEvent>()

    private lateinit var consumer: OrderCancellationResultConsumer

    @BeforeEach
    fun setUp() {
        appender.start()
        logger.addAppender(appender)
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            invocation.getArgument<TransactionCallback<Any>>(0).doInTransaction(mock())
        }
        consumer = OrderCancellationResultConsumer(
            orderRepository, orderItemRepository, sagaEventLogRepository,
            inventoryCommandPort, transactionalEventPublisher,
            ObjectMapper(), transactionTemplate
        )
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun 이미_PAID인_주문에_늦게_온_취소_실패_이벤트는_전환_WARN을_남기지_않는다() {
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.PAID)))

        consumer.handleCancellationResult(failedMessage())

        assertThat(transitionWarnings()).isEmpty()
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun 주문이_없으면_전환_WARN을_남기지_않는다() {
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty())

        consumer.handleCancellationResult(failedMessage())

        assertThat(transitionWarnings()).isEmpty()
    }

    @Test
    fun CANCEL_REQUESTED_주문을_CANCEL_FAILED로_바꾸면_전환_WARN을_한_번_남긴다() {
        val order = order(OrderStatus.CANCEL_REQUESTED)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))

        consumer.handleCancellationResult(failedMessage())

        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_FAILED)
        assertThat(transitionWarnings()).hasSize(1)
    }

    private fun transitionWarnings() = appender.list.filter {
        it.level == Level.WARN && it.formattedMessage.contains("CANCEL_FAILED 전환")
    }

    private fun failedMessage() =
        """{"eventType":"PaymentCancellationFailed","eventId":"evt-1","orderId":$ORDER_ID,"reason":"PG 오류"}"""

    private fun order(status: OrderStatus): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        if (status == OrderStatus.CANCEL_REQUESTED) {
            order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        }
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
    }

    companion object {
        private const val ORDER_ID = 100L
    }
}
