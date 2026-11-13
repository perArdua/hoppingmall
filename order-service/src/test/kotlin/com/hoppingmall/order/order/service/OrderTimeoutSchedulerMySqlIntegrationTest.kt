package com.hoppingmall.order.order.service

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
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

@DisplayName("주문 시간 초과 스케줄러 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderTimeoutSchedulerMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

    private val restTemplate: RestTemplate = mock()

    private val inventoryCommandPort: InventoryCommandPort = mock()

    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private lateinit var scheduler: OrderTimeoutScheduler

    private lateinit var otherInstance: OrderTimeoutScheduler

    @BeforeEach
    fun setUp() {
        scheduler = newScheduler()
        otherInstance = newScheduler()
    }

    @Test
    fun 십오분_넘게_PAYING인_주문에_결제가_없으면_CANCELLED로_바꾸고_예약을_취소한다() {
        val orderId = savePayingOrder("rsv-stale", minutesAgo = 20)
        givenNoPayment(orderId)
        givenPaymentCancelNotFound(orderId)

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-stale"))
    }

    @Test
    fun 십오분_넘게_PAYING인_주문의_결제가_SUCCESS면_그대로_두고_결제_완료_이벤트를_기다린다() {
        val orderId = savePayingOrder("rsv-paid", minutesAgo = 20)
        givenPayment(orderId, "SUCCESS")

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAYING.name)
        verify(restTemplate, never()).postForEntity(any<String>(), anyOrNull(), eq(Void::class.java))
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 십오분이_안_된_PAYING_주문은_건드리지_않는다() {
        val orderId = savePayingOrder("rsv-fresh", minutesAgo = 10)
        givenNoPayment(orderId)
        givenPaymentCancelNotFound(orderId)

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAYING.name)
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 결제_조회가_연결_실패면_PAYING_주문을_그대로_둔다() {
        val orderId = savePayingOrder("rsv-unknown", minutesAgo = 20)
        whenever(restTemplate.getForObject(paymentUrl(orderId), PaymentInfo::class.java))
            .thenThrow(ResourceAccessException("connection refused"))
        givenPaymentCancelNotFound(orderId)

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAYING.name)
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 오분_넘게_CANCEL_REQUESTED인_주문의_결제가_CANCELLED면_주문을_CANCELLED로_하고_확정_예약_취소를_부른다() {
        val orderId = saveCancelRequestedOrder("rsv-confirmed", minutesAgo = 6)
        givenPayment(orderId, "CANCELLED")

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        verify(inventoryCommandPort, times(1)).cancelConfirmedReservations(listOf("rsv-confirmed"))
        assertThat(cancellationRequestEventIds(orderId)).isEmpty()
    }

    @Test
    fun 오분_넘게_CANCEL_REQUESTED인_주문의_결제가_SUCCESS면_세_번까지_새_eventId로_다시_요청하고_그다음은_CANCEL_FAILED다() {
        val orderId = saveCancelRequestedOrder("rsv-stuck", minutesAgo = 6)
        givenPayment(orderId, "SUCCESS")

        repeat(3) {
            scheduler.checkTimedOutOrders()
            assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
            ageOrder(orderId, minutesAgo = 6)
        }
        val eventIds = cancellationRequestEventIds(orderId)
        assertThat(eventIds).hasSize(3).doesNotHaveDuplicates()
        eventIds.forEach { assertThat(it).doesNotMatch("cancel-\\d+-\\d+") }

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_FAILED.name)
        assertThat(cancellationRequestEventIds(orderId)).hasSize(3)
        verify(inventoryCommandPort, never()).cancelConfirmedReservations(any())
    }

    @Test
    fun 오분이_안_된_CANCEL_REQUESTED_주문은_건드리지_않는다() {
        val orderId = saveCancelRequestedOrder("rsv-recent", minutesAgo = 3)
        givenPayment(orderId, "SUCCESS")

        scheduler.checkTimedOutOrders()

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
        assertThat(cancellationRequestEventIds(orderId)).isEmpty()
    }

    @Test
    fun 두_인스턴스가_같은_PAYING_주문을_동시에_처리해도_결제_취소와_예약_취소는_한_번이다() {
        val orderId = savePayingOrder("rsv-two-instances", minutesAgo = 20)
        givenNoPayment(orderId)
        givenPaymentCancelNotFound(orderId)

        runBothInstancesWhileOrderIsLocked(orderId)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        verify(restTemplate, times(1)).postForEntity(eq(cancelUrl(orderId)), anyOrNull(), eq(Void::class.java))
        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-two-instances"))
    }

    @Test
    fun 두_인스턴스가_같은_CANCEL_REQUESTED_주문을_동시에_처리해도_취소_요청은_한_번만_다시_낸다() {
        val orderId = saveCancelRequestedOrder("rsv-two-retries", minutesAgo = 6)
        givenPayment(orderId, "SUCCESS")

        runBothInstancesWhileOrderIsLocked(orderId)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
        assertThat(cancellationRequestEventIds(orderId)).hasSize(1)
    }

    private fun runBothInstancesWhileOrderIsLocked(orderId: Long) {
        val enteredAt = AtomicLong()
        val runs = dataSource.connection.use { holder ->
            holder.autoCommit = false
            holder.prepareStatement("SELECT id FROM orders WHERE id = ? FOR UPDATE").use {
                it.setLong(1, orderId)
                it.executeQuery().close()
            }
            val runs = listOf(scheduler, otherInstance).map { instance ->
                executor.submit(Callable {
                    enteredAt.compareAndSet(0L, System.currentTimeMillis())
                    instance.checkTimedOutOrders()
                })
            }
            awaitOrdersLockWaiters(runs, enteredAt)
            holder.rollback()
            runs
        }
        runs.forEach { it.get(LIMIT_SECONDS, TimeUnit.SECONDS) }
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
            transactionalEventPublisher, mock<OrderMetrics>(), transactionTemplate
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

    private fun savePayingOrder(reservationId: String, minutesAgo: Long): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        return saveWithItem(order, reservationId, minutesAgo)
    }

    private fun saveCancelRequestedOrder(reservationId: String, minutesAgo: Long): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        return saveWithItem(order, reservationId, minutesAgo)
    }

    private fun saveWithItem(order: Order, reservationId: String, minutesAgo: Long): Long {
        val orderId = orderRepository.save(order).id!!
        orderItemRepository.save(
            OrderItem.create(
                orderId = orderId, sellerId = 5L, productId = 100L,
                productName = "상품", productPrice = BigDecimal("50000"),
                quantity = 1, reservationId = reservationId
            )
        )
        ageOrder(orderId, minutesAgo)
        return orderId
    }

    private fun ageOrder(orderId: Long, minutesAgo: Long) {
        jdbcTemplate.update(
            "UPDATE orders SET updated_at = ? WHERE id = ?",
            LocalDateTime.now().minusMinutes(minutesAgo),
            orderId
        )
    }

    private fun givenNoPayment(orderId: Long) {
        whenever(restTemplate.getForObject(paymentUrl(orderId), PaymentInfo::class.java))
            .thenThrow(notFound())
    }

    private fun givenPayment(orderId: Long, status: String) {
        whenever(restTemplate.getForObject(paymentUrl(orderId), PaymentInfo::class.java))
            .thenReturn(
                PaymentInfo(
                    id = 900L, orderId = orderId, amount = BigDecimal("50000"),
                    pointAmount = BigDecimal.ZERO, couponId = null, status = status
                )
            )
    }

    private fun givenPaymentCancelNotFound(orderId: Long) {
        whenever(restTemplate.postForEntity(eq(cancelUrl(orderId)), anyOrNull(), eq(Void::class.java)))
            .thenThrow(notFound())
    }

    private fun notFound(): HttpClientErrorException =
        HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, ByteArray(0), null)

    private fun paymentUrl(orderId: Long) = "$PAYMENT_URL/internal/api/v1/payments/by-order/$orderId"

    private fun cancelUrl(orderId: Long) = "$PAYMENT_URL/internal/api/v1/payments/by-order/$orderId/cancel"

    private fun cancellationRequestEventIds(orderId: Long): List<String> {
        val captor = argumentCaptor<Any>()
        verify(transactionalEventPublisher, atLeast(0)).publishEvent(
            any(), eq(orderId.toString()), eq("PaymentCancellationRequested"), captor.capture(), any(), any()
        )
        return captor.allValues.map { (it as Map<*, *>)["eventId"].toString() }
    }

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    private fun awaitOrdersLockWaiters(pending: List<Future<*>>, enteredAt: AtomicLong) {
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + LIMIT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount("orders") >= pending.size) return
            if (pending.any { it.isDone }) {
                throw AssertionError("orders 잠금을 기다리지 않고 끝난 인스턴스가 있다\n" + pending.joinToString("\n") { lockDiagnostics(it, enteredAt, startedAt) })
            }
            Thread.onSpinWait()
        }
        throw AssertionError("두 인스턴스의 orders 잠금 대기가 보이지 않았다\n" + pending.joinToString("\n") { lockDiagnostics(it, enteredAt, startedAt) })
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val LIMIT_SECONDS = 20L
        private const val PAYMENT_URL = "http://localhost:8085"
    }
}
