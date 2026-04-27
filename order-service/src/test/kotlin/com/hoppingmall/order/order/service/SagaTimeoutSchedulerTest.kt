package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("SagaTimeoutScheduler")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class SagaTimeoutSchedulerTest {

    @Mock
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var scheduler: SagaTimeoutScheduler

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        scheduler = SagaTimeoutScheduler(
            sagaEventLogRepository,
            orderRepository,
            transactionalEventPublisher,
            transactionTemplate
        )
    }

    @Test
    fun 타임아웃된_saga를_감지하여_보상_트랜잭션을_트리거한다() {
        val saga = SagaEventLog(
            eventId = "payment-completed-txn-1",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L,
            timeoutAt = LocalDateTime.now().minusMinutes(1)
        ).apply {
            id = 100L
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        }
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)

        whenever(sagaEventLogRepository.findTimedOutSagas(any())).thenReturn(listOf(saga))
        whenever(sagaEventLogRepository.findById(100L)).thenReturn(Optional.of(saga))
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        scheduler.checkTimedOutSagas()

        assertThat(saga.isTimedOut()).isTrue()
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Order"),
            aggregateId = eq("1"),
            eventType = eq("PaymentReversalRequested"),
            eventData = any(),
            topic = eq("payment-reversal"),
            partitionKey = eq("1")
        )
    }

    @Test
    fun 타임아웃된_saga가_없으면_아무것도_하지_않는다() {
        whenever(sagaEventLogRepository.findTimedOutSagas(any())).thenReturn(emptyList())

        scheduler.checkTimedOutSagas()

        verify(sagaEventLogRepository, never()).findById(any())
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun 이미_타임아웃_처리된_saga는_중복_보상하지_않는다() {
        val saga = SagaEventLog(
            eventId = "payment-completed-txn-2",
            eventType = "PAYMENT_COMPLETED",
            orderId = 2L,
            timeoutAt = LocalDateTime.now().minusMinutes(1)
        ).apply {
            id = 200L
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            markAsTimedOut()
        }

        whenever(sagaEventLogRepository.findTimedOutSagas(any())).thenReturn(listOf(saga))
        whenever(sagaEventLogRepository.findById(200L)).thenReturn(Optional.of(saga))

        scheduler.checkTimedOutSagas()

        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun 이미_완료된_saga는_보상하지_않는다() {
        val saga = SagaEventLog(
            eventId = "payment-completed-txn-3",
            eventType = "PAYMENT_COMPLETED",
            orderId = 3L,
            timeoutAt = LocalDateTime.now().minusMinutes(1)
        ).apply {
            id = 300L
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
        }

        whenever(sagaEventLogRepository.findTimedOutSagas(any())).thenReturn(listOf(saga))
        whenever(sagaEventLogRepository.findById(300L)).thenReturn(Optional.of(saga))

        scheduler.checkTimedOutSagas()

        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), any(), any(), any(), any()
        )
    }
}
