package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.dto.event.PaymentCompletedEvent
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderPaymentCancellationFailedException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("주문 트랜잭션 경계")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@DataJpaTest
@Import(
    OrderCommandServiceImpl::class,
    OrderSagaConsumer::class,
    OrderTransactionalRollbackTest.TestConfig::class
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderTransactionalRollbackTest {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var orderSagaConsumer: OrderSagaConsumer

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Autowired
    private lateinit var txTemplate: TransactionTemplate

    @MockitoBean
    private lateinit var cartItemRepository: CartItemRepository

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var orderMetrics: OrderMetrics

    @MockitoBean
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @MockitoBean
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun compensateFailedConfirmation_중_역보상_이벤트_발행이_실패하면_주문_취소가_롤백된다() {
        val order = orderRepository.saveAndFlush(createPaidOrder())
        val sagaLog = SagaEventLog(
            eventId = "payment-completed-txn-123",
            eventType = "PAYMENT_COMPLETED",
            orderId = order.id!!
        ).apply {
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        }
        sagaEventLogRepository.saveAndFlush(sagaLog)

        doThrow(RuntimeException("outbox publish failed")).whenever(transactionalEventPublisher).publishEvent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )

        val event = PaymentCompletedEvent(
            paymentId = 100L,
            orderId = order.id!!,
            userId = order.buyerId,
            amount = order.totalAmount,
            pointAmount = BigDecimal.ZERO,
            transactionId = "txn-123"
        )

        val thrown = catchThrowable {
            orderSagaConsumer.compensateFailedConfirmation("payment-completed-txn-123", event)
        }

        assertThat(thrown).isInstanceOf(RuntimeException::class.java)
        val propagatedMessages = listOfNotNull(thrown?.message, thrown?.cause?.message).joinToString(" | ")
        assertThat(propagatedMessages).contains("outbox publish failed")

        val reloadedOrder = orderRepository.findById(order.id!!).orElseThrow()
        assertThat(reloadedOrder.status).isEqualTo(OrderStatus.PAID)

        txTemplate.execute {
            val reloadedLog = sagaEventLogRepository.findByEventId("payment-completed-txn-123")!!
            assertThat(reloadedLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isFalse()
        }
    }

    @Test
    fun cancelPayment이_false면_주문_취소를_롤백하고_재고_보상도_중단한다() {
        val order = orderRepository.saveAndFlush(createPayingOrder())
        orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = order.id!!,
                sellerId = 2L,
                productId = 3L,
                productName = "상품A",
                productPrice = BigDecimal("50000"),
                quantity = 1,
                reservationId = "rsv-1"
            )
        )

        whenever(paymentCommandPort.cancelPayment(order.id!!)).thenReturn(false)

        assertThatThrownBy {
            orderCommandService.cancelOrder(order.buyerId, order.id!!)
        }.isInstanceOf(OrderPaymentCancellationFailedException::class.java)

        val reloadedOrder = orderRepository.findById(order.id!!).orElseThrow()

        assertThat(reloadedOrder.status).isEqualTo(OrderStatus.PAYING)
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    private fun createPaidOrder(): Order {
        return Order.create(
            buyerId = 10L,
            totalAmount = BigDecimal("50000")
        ).apply {
            updateStatus(OrderStatus.PAYING)
            updateStatus(OrderStatus.PAID)
        }
    }

    private fun createPayingOrder(): Order {
        return Order.create(
            buyerId = 10L,
            totalAmount = BigDecimal("50000")
        ).apply {
            updateStatus(OrderStatus.PAYING)
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate {
            return TransactionTemplate(transactionManager)
        }
    }
}
