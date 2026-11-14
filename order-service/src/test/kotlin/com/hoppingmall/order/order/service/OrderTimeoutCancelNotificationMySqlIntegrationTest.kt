package com.hoppingmall.order.order.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.HttpPaymentCommandAdapter
import com.hoppingmall.order.port.HttpPaymentQueryAdapter
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("취소 요청 시간 초과로 취소된 주문의 취소 완료 알림")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderTimeoutCancelNotificationMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val restTemplate: RestTemplate = mock()

    private val inventoryCommandPort: InventoryCommandPort = mock()

    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private lateinit var scheduler: OrderTimeoutScheduler

    @BeforeEach
    fun setUp() {
        scheduler = newScheduler()
    }

    @Test
    fun 취소_실패를_거쳐_다시_요청한_취소가_시간_초과에서_결제_취소로_확인되면_취소_완료_알림을_보낸다() {
        val orderId = saveOrder(
            listOf(
                OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED,
                OrderStatus.CANCEL_FAILED, OrderStatus.CANCEL_REQUESTED
            )
        )
        givenPayment(orderId, "CANCELLED")

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        val notifications = cancellationNotifications(orderId)
        assertThat(notifications).hasSize(1)
        val notification = notifications.single()
        assertThat(notification["eventType"]).isEqualTo("OrderCancellationNotificationRequested")
        assertThat(notification["userId"]).isEqualTo(BUYER_ID)
        assertThat(notification["type"]).isEqualTo("PAYMENT_CANCELLED")
        assertThat(notification["title"]).isEqualTo("주문이 취소되었습니다")
        assertThat(notification["eventId"].toString()).isNotBlank()
    }

    @Test
    fun 취소_실패를_거치지_않은_취소_요청도_시간_초과에서_취소되면_취소_완료_알림을_보낸다() {
        val orderId = saveOrder(listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED))
        givenPayment(orderId, "CANCELLED")

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        assertThat(cancellationNotifications(orderId)).hasSize(1)
    }

    @Test
    fun 취소된_뒤_다시_돌아도_취소_완료_알림은_한_번이다() {
        val orderId = saveOrder(listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED))
        givenPayment(orderId, "CANCELLED")

        scheduler.checkTimedOutOrders()
        ageOrder(orderId)
        scheduler.checkTimedOutOrders()

        assertThat(cancellationNotifications(orderId)).hasSize(1)
    }

    @Test
    fun 결제가_아직_취소되지_않았으면_취소_완료_알림을_보내지_않는다() {
        val orderId = saveOrder(listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED))
        givenPayment(orderId, "SUCCESS")

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
        assertThat(cancellationNotifications(orderId)).isEmpty()
    }

    private fun newScheduler(): OrderTimeoutScheduler {
        val restTemplateBuilder: RestTemplateBuilder = mock()
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        val transactionTemplate = TransactionTemplate(transactionManager)
        val orderCommandService = OrderCommandServiceImpl(
            orderRepository, orderItemRepository, mock<CartItemRepository>(),
            mock<ProductQueryPort>(), inventoryCommandPort,
            HttpPaymentCommandAdapter(PAYMENT_URL, restTemplateBuilder),
            transactionalEventPublisher, mock<OrderMetrics>(), transactionTemplate, mock<RefundRepository>()
        )
        return OrderTimeoutScheduler(
            orderRepository,
            orderItemRepository,
            orderCommandService,
            HttpPaymentQueryAdapter(PAYMENT_URL, restTemplateBuilder),
            inventoryCommandPort,
            transactionalEventPublisher,
            transactionTemplate
        )
    }

    private fun saveOrder(path: List<OrderStatus>): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        path.forEach { order.updateStatus(it) }
        val orderId = orderRepository.save(order).id!!
        orderItemRepository.save(
            OrderItem.create(
                orderId = orderId, sellerId = 5L, productId = 100L,
                productName = "상품", productPrice = BigDecimal("50000"),
                quantity = 1, reservationId = "rsv-$orderId"
            )
        )
        ageOrder(orderId)
        return orderId
    }

    private fun ageOrder(orderId: Long) {
        jdbcTemplate.update(
            "UPDATE orders SET updated_at = ? WHERE id = ?",
            LocalDateTime.now().minusMinutes(6),
            orderId
        )
    }

    private fun givenPayment(orderId: Long, status: String) {
        whenever(restTemplate.getForObject("$PAYMENT_URL/internal/api/v1/payments/by-order/$orderId", PaymentInfo::class.java))
            .thenReturn(
                PaymentInfo(
                    id = 900L, orderId = orderId, amount = BigDecimal("50000"),
                    pointAmount = BigDecimal.ZERO, couponId = null, status = status
                )
            )
    }

    private fun cancellationNotifications(orderId: Long): List<Map<*, *>> {
        val captor = argumentCaptor<Any>()
        verify(transactionalEventPublisher, atLeast(0)).publishEvent(
            any(), eq(orderId.toString()), eq("OrderCancellationNotificationRequested"), captor.capture(),
            eq(KafkaTopics.NOTIFICATION), any()
        )
        return captor.allValues.map { it as Map<*, *> }
    }

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    companion object {
        private const val BUYER_ID = 10L
        private const val PAYMENT_URL = "http://localhost:8085"
    }
}
