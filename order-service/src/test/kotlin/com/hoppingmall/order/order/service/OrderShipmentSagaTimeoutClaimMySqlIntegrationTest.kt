package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.service.ShippingCommandServiceImpl
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("saga 시간 초과 선점 중인 주문의 배송 전환 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    OrderCommandServiceImpl::class,
    ShippingCommandServiceImpl::class,
    OrderShipmentSagaTimeoutClaimMySqlIntegrationTest.TestConfig::class
)
class OrderShipmentSagaTimeoutClaimMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var shippingCommandService: ShippingCommandServiceImpl

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @MockitoBean
    private lateinit var orderMetrics: OrderMetrics

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM shippings")
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
    }

    @Test
    fun 시간_초과_보상이_선점한_주문은_관리자가_SHIPPED로_바꿀_수_없다() {
        val orderId = savePaidOrder()
        savePaymentCompletedSaga(orderId, claimed = true)

        assertThatThrownBy {
            orderCommandService.updateOrderStatus(orderId, OrderStatusUpdateRequest(OrderStatus.SHIPPED), ADMIN_ID, true)
        }.isInstanceOf(OrderInvalidStatusException::class.java)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID.name)
    }

    @Test
    fun 시간_초과_보상이_선점한_주문의_배송은_배송_중으로_바꿀_수_없다() {
        val orderId = savePaidOrder()
        savePaymentCompletedSaga(orderId, claimed = true)
        val shippingId = saveShipping(orderId)

        assertThatThrownBy {
            shippingCommandService.updateShippingStatus(SELLER_ID, shippingId, ShippingStatusUpdateRequest(ShippingStatus.IN_TRANSIT))
        }.isInstanceOf(OrderInvalidStatusException::class.java)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID.name)
        assertThat(shippingStatus(shippingId)).isEqualTo(ShippingStatus.PREPARING.name)
    }

    @Test
    fun 선점하지_않은_결제_완료_saga가_있는_주문은_SHIPPED로_바꾼다() {
        val orderId = savePaidOrder()
        savePaymentCompletedSaga(orderId, claimed = false)

        orderCommandService.updateOrderStatus(orderId, OrderStatusUpdateRequest(OrderStatus.SHIPPED), ADMIN_ID, true)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.SHIPPED.name)
    }

    @Test
    fun 배송_전환이_주문_잠금을_기다리는_사이_시간_초과_보상이_선점하면_배송_전환을_거절한다() {
        val orderId = savePaidOrder()
        val eventId = savePaymentCompletedSaga(orderId, claimed = false)
        val shippingId = saveShipping(orderId)
        val claimLocked = CountDownLatch(1)
        val releaseClaim = CountDownLatch(1)
        val transactionTemplate = TransactionTemplate(transactionManager)

        val claim = executor.submit {
            transactionTemplate.executeWithoutResult {
                orderRepository.lockById(orderId)
                claimLocked.countDown()
                val saga = sagaEventLogRepository.findByEventId(eventId)!!
                saga.claimTimeout()
                sagaEventLogRepository.save(saga)
                releaseClaim.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        }
        assertThat(claimLocked.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

        val enteredAt = AtomicLong()
        val shipment = executor.submit {
            enteredAt.set(System.currentTimeMillis())
            shippingCommandService.updateShippingStatus(SELLER_ID, shippingId, ShippingStatusUpdateRequest(ShippingStatus.IN_TRANSIT))
        }
        awaitLockWait(shipment, enteredAt, "배송 전환", "orders")
        releaseClaim.countDown()
        claim.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertThatThrownBy { shipment.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .hasCauseInstanceOf(OrderInvalidStatusException::class.java)
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID.name)
        assertThat(shippingStatus(shippingId)).isEqualTo(ShippingStatus.PREPARING.name)
    }

    private fun savePaidOrder(): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("5000.00"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        return orderRepository.save(order).id!!
    }

    private fun savePaymentCompletedSaga(orderId: Long, claimed: Boolean): String {
        val eventId = "payment-completed-txn-shipment-$orderId"
        val saga = SagaEventLog(eventId = eventId, eventType = "PAYMENT_COMPLETED", orderId = orderId, paymentId = 100L)
        saga.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        if (claimed) {
            saga.claimTimeout()
        }
        sagaEventLogRepository.save(saga)
        return eventId
    }

    private fun saveShipping(orderId: Long): Long =
        shippingRepository.save(
            Shipping.create(
                orderId = orderId, buyerId = BUYER_ID, carrierName = "CJ대한통운",
                trackingNumber = "TRK-$orderId", recipientName = "홍길동",
                recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
            )
        ).id!!

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    private fun shippingStatus(shippingId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM shippings WHERE id = ?", String::class.java, shippingId)!!

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 20L
        private const val ADMIN_ID = 1L
        private const val LATCH_TIMEOUT_SECONDS = 20L
    }
}
