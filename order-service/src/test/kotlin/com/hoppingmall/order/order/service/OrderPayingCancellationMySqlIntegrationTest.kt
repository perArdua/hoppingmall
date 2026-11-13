package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.port.HttpPaymentCommandAdapter
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

@DisplayName("결제 전 주문 취소와 늦게 온 결제 완료 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    OrderCommandServiceImpl::class,
    OrderSagaConsumer::class,
    HttpPaymentCommandAdapter::class,
    OrderPayingCancellationMySqlIntegrationTest.TestConfig::class
)
@ImportAutoConfiguration(AopAutoConfiguration::class, CircuitBreakerAutoConfiguration::class, RetryAutoConfiguration::class)
class OrderPayingCancellationMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var orderSagaConsumer: OrderSagaConsumer

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var paymentServer: MockServerRestTemplateCustomizer

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    private lateinit var dataSource: DataSource

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @MockitoBean
    private lateinit var orderMetrics: OrderMetrics

    @MockitoBean
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
        paymentServer.server.reset()
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    @Test
    fun 결제가_없는_PAYING_주문을_취소하면_CANCELLED이고_예약_취소를_한_번_부른다() {
        val orderId = savePayingOrder("rsv-no-payment")
        expectPaymentCancel(orderId).andRespond(withStatus(HttpStatus.NOT_FOUND))

        val response = orderCommandService.cancelOrder(BUYER_ID, orderId)

        assertThat(response.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-no-payment"))
        paymentServer.server.verify()
    }

    @Test
    fun 결제_취소_호출이_실패해도_주문은_CANCELLED이고_예약을_취소한다() {
        val orderId = savePayingOrder("rsv-payment-down")
        expectPaymentCancel(orderId).andRespond(withServerError())

        val response = orderCommandService.cancelOrder(BUYER_ID, orderId)

        assertThat(response.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-payment-down"))
    }

    @Test
    fun 사용자_취소와_결제_완료가_동시에_오면_주문은_한_결론이고_CANCELLED가_이기면_취소_요청_이벤트가_한_건이다() {
        val orderId = savePayingOrder("rsv-race")
        paymentServer.server.expect(ExpectedCount.manyTimes(), requestTo(cancelUrl(orderId)))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())
        whenever(inventoryCommandPort.confirmReservations(any())).thenReturn(true)

        val enteredAt = AtomicLong()
        val (cancel, completed) = dataSource.connection.use { holder ->
            holder.autoCommit = false
            holder.prepareStatement("SELECT id FROM orders WHERE id = ? FOR UPDATE").use {
                it.setLong(1, orderId)
                it.executeQuery().close()
            }
            val cancel = executor.submit(Callable {
                enteredAt.compareAndSet(0L, System.currentTimeMillis())
                runCatching { orderCommandService.cancelOrder(BUYER_ID, orderId) }
            })
            val completed = executor.submit(Callable {
                enteredAt.compareAndSet(0L, System.currentTimeMillis())
                runCatching { orderSagaConsumer.handlePaymentCompleted(paymentCompletedMessage(orderId, "txn-race")) }
            })
            awaitOrdersLockWaiters(listOf(cancel, completed), enteredAt)
            holder.rollback()
            cancel to completed
        }

        val cancelResult = cancel.get(LIMIT_SECONDS, TimeUnit.SECONDS)
        assertThat(completed.get(LIMIT_SECONDS, TimeUnit.SECONDS).isSuccess).isTrue()
        val finalStatus = orderStatus(orderId)
        assertThat(finalStatus).isIn(OrderStatus.CANCELLED.name, OrderStatus.PAID.name)

        if (finalStatus == OrderStatus.CANCELLED.name) {
            assertThat(cancelResult.isSuccess).isTrue()
            assertThat(cancellationRequestEventIds(orderId)).hasSize(1)
            verify(inventoryCommandPort, never()).confirmReservations(any())
            verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-race"))
        } else {
            assertThat(cancelResult.exceptionOrNull()).isInstanceOf(OrderInvalidStatusException::class.java)
            assertThat(cancellationRequestEventIds(orderId)).isEmpty()
            verify(inventoryCommandPort, never()).cancelReservations(any())
            verify(inventoryCommandPort, times(1)).confirmReservations(listOf("rsv-race"))
        }
    }

    @Test
    fun CANCELLED_주문에_결제_완료_이벤트가_오면_취소_요청_이벤트_한_건을_내고_주문은_그대로다() {
        val orderId = savePayingOrder("rsv-late")
        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", orderId)

        orderSagaConsumer.handlePaymentCompleted(paymentCompletedMessage(orderId, "txn-late"))
        orderSagaConsumer.handlePaymentCompleted(paymentCompletedMessage(orderId, "txn-late"))

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        val eventIds = cancellationRequestEventIds(orderId)
        assertThat(eventIds).hasSize(1)
        assertThat(eventIds.single()).doesNotMatch("cancel-\\d+-\\d+")
        verify(inventoryCommandPort, never()).confirmReservations(any())
    }

    @Test
    fun CANCEL_FAILED_주문을_다시_취소하면_CANCEL_REQUESTED로_돌아가고_취소_요청을_다시_낸다() {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        order.updateStatus(OrderStatus.CANCEL_FAILED)
        val orderId = orderRepository.save(order).id!!

        val response = orderCommandService.cancelOrder(BUYER_ID, orderId)

        assertThat(response.status).isEqualTo(OrderStatus.CANCEL_REQUESTED)
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
        assertThat(cancellationRequestEventIds(orderId)).hasSize(1)
    }

    private fun savePayingOrder(reservationId: String): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        val orderId = orderRepository.save(order).id!!
        orderItemRepository.save(
            OrderItem.create(
                orderId = orderId, sellerId = 5L, productId = 100L,
                productName = "상품", productPrice = BigDecimal("50000"),
                quantity = 1, reservationId = reservationId
            )
        )
        return orderId
    }

    private fun expectPaymentCancel(orderId: Long) =
        paymentServer.server.expect(ExpectedCount.once(), requestTo(cancelUrl(orderId)))
            .andExpect(method(HttpMethod.POST))

    private fun cancelUrl(orderId: Long) = "$PAYMENT_URL/internal/api/v1/payments/by-order/$orderId/cancel"

    private fun paymentCompletedMessage(orderId: Long, transactionId: String): String =
        """{"paymentId":900,"orderId":$orderId,"userId":$BUYER_ID,"amount":50000,"pointAmount":0,"transactionId":"$transactionId"}"""

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
                throw AssertionError("orders 잠금을 기다리지 않고 끝난 작업이 있다\n" + pending.joinToString("\n") { lockDiagnostics(it, enteredAt, startedAt) })
            }
            Thread.onSpinWait()
        }
        throw AssertionError("두 작업의 orders 잠금 대기가 보이지 않았다\n" + pending.joinToString("\n") { lockDiagnostics(it, enteredAt, startedAt) })
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun mockServerRestTemplateCustomizer(): MockServerRestTemplateCustomizer = MockServerRestTemplateCustomizer()

        @Bean
        fun restTemplateBuilder(customizer: MockServerRestTemplateCustomizer): RestTemplateBuilder =
            RestTemplateBuilder(customizer)

        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()

        @Bean
        fun kafkaEventMapper(objectMapper: ObjectMapper): KafkaEventMapper = KafkaEventMapper(objectMapper)
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val LIMIT_SECONDS = 20L
        private const val PAYMENT_URL = "http://localhost:8085"
    }
}
