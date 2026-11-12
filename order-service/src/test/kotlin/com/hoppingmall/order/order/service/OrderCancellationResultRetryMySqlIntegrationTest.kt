package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.ResourceAccessException
import java.math.BigDecimal

@DisplayName("결제 취소 완료 처리의 재고 호출 재시도 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCancellationResultRetryMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val inventoryCommandPort: InventoryCommandPort = mock()

    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private val objectMapper = ObjectMapper()

    private lateinit var consumer: OrderCancellationResultConsumer

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
        consumer = OrderCancellationResultConsumer(
            orderRepository,
            orderItemRepository,
            sagaEventLogRepository,
            inventoryCommandPort,
            transactionalEventPublisher,
            objectMapper,
            TransactionTemplate(transactionManager)
        )
    }

    @Test
    fun 확정된_예약_취소_호출이_실패한_메시지가_다시_오면_확정된_예약_취소를_다시_부른다() {
        val orderId = saveCancelRequestedOrder()
        doThrow(ResourceAccessException("product 연결 실패"))
            .doNothing()
            .whenever(inventoryCommandPort).cancelConfirmedReservations(any())

        assertThatThrownBy { consumer.handleCancellationResult(completedMessage(orderId)) }
            .isInstanceOf(ResourceAccessException::class.java)
        assertThatCode { consumer.handleCancellationResult(completedMessage(orderId)) }
            .doesNotThrowAnyException()

        verify(inventoryCommandPort, times(2)).cancelConfirmedReservations(listOf(RESERVATION_ID))
        assertThat(sagaSteps()).containsExactlyInAnyOrder("LOCAL_COMPLETED", "REMOTE_COMPLETED")
    }

    @Test
    fun 재고_호출이_실패해도_주문은_CANCELLED가_되고_재전달_때_상태_전이와_취소_알림은_다시_하지_않는다() {
        val orderId = saveCancelRequestedOrder()
        doThrow(ResourceAccessException("product 연결 실패"))
            .doNothing()
            .whenever(inventoryCommandPort).cancelConfirmedReservations(any())

        assertThatThrownBy { consumer.handleCancellationResult(completedMessage(orderId)) }
            .isInstanceOf(ResourceAccessException::class.java)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        assertThat(sagaSteps()).containsExactly("LOCAL_COMPLETED")
        verifyCancellationNotificationPublished(count = 1)

        consumer.handleCancellationResult(completedMessage(orderId))

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        assertThat(sagaLogCount()).isEqualTo(1)
        verifyCancellationNotificationPublished(count = 1)
    }

    @Test
    fun 확정된_예약_취소가_성공한_뒤의_재전달은_재고와_알림을_다시_부르지_않는다() {
        val orderId = saveCancelRequestedOrder()

        consumer.handleCancellationResult(completedMessage(orderId))
        assertThatCode { consumer.handleCancellationResult(completedMessage(orderId)) }
            .doesNotThrowAnyException()

        verify(inventoryCommandPort, times(1)).cancelConfirmedReservations(listOf(RESERVATION_ID))
        verifyCancellationNotificationPublished(count = 1)
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        assertThat(sagaSteps()).containsExactlyInAnyOrder("LOCAL_COMPLETED", "REMOTE_COMPLETED")
    }

    private fun saveCancelRequestedOrder(): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        val orderId = orderRepository.save(order).id!!
        orderItemRepository.save(
            OrderItem.create(
                orderId = orderId,
                sellerId = 1L,
                productId = 100L,
                productName = "상품-100",
                productPrice = BigDecimal("25000"),
                quantity = 2,
                reservationId = RESERVATION_ID
            )
        )
        return orderId
    }

    private fun completedMessage(orderId: Long): String = objectMapper.writeValueAsString(
        mapOf(
            "eventType" to "PaymentCancellationCompleted",
            "eventId" to EVENT_ID,
            "orderId" to orderId,
            "paymentId" to 1L
        )
    )

    private fun verifyCancellationNotificationPublished(count: Int) {
        verify(transactionalEventPublisher, times(count)).publishEvent(
            any(), any(), eq("OrderCancellationNotificationRequested"), any(), any(), any()
        )
    }

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    private fun sagaLogCount(): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM saga_event_logs WHERE event_id = ?", Int::class.java, EVENT_ID
        )!!

    private fun sagaSteps(): List<String> =
        jdbcTemplate.queryForList(
            "SELECT s.step FROM saga_event_log_steps s JOIN saga_event_logs l ON l.id = s.saga_event_log_id WHERE l.event_id = ?",
            String::class.java,
            EVENT_ID
        )

    companion object {
        private const val BUYER_ID = 10L
        private const val EVENT_ID = "cancel-completed-retry-1"
        private const val RESERVATION_ID = "11111111-1111-1111-1111-111111111111"
    }
}
