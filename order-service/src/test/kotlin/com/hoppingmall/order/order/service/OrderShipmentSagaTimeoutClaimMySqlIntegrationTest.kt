package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.dto.response.ShippingResponse
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.exception.ShippingAlreadyExistsException
import com.hoppingmall.order.shipping.exception.ShippingInvalidStatusException
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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.sql.Connection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

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
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

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
        val shippingId = saveShipping(orderId)

        assertThatThrownBy {
            shippingCommandService.updateStatusByOrder(ADMIN_ID, orderId, OrderStatus.SHIPPED, true)
        }.isInstanceOf(OrderInvalidStatusException::class.java)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID.name)
        assertThat(shippingStatus(shippingId)).isEqualTo(ShippingStatus.PREPARING.name)
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
        saveShipping(orderId)

        shippingCommandService.updateStatusByOrder(ADMIN_ID, orderId, OrderStatus.SHIPPED, true)

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

    @Test
    fun 배송_등록_뒤_결제_완료_주문을_취소하면_배송도_CANCELLED가_되고_그_배송은_더_진행되지_않는다() {
        val orderId = savePaidOrder()
        val shippingId = saveShipping(orderId)

        orderCommandService.cancelOrder(BUYER_ID, orderId)

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
        assertThat(shippingStatus(shippingId)).isEqualTo(ShippingStatus.CANCELLED.name)
        assertThatThrownBy {
            shippingCommandService.updateShippingStatus(SELLER_ID, shippingId, ShippingStatusUpdateRequest(ShippingStatus.IN_TRANSIT))
        }.isInstanceOf(ShippingInvalidStatusException::class.java)
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
        assertThat(shippingStatus(shippingId)).isEqualTo(ShippingStatus.CANCELLED.name)
    }

    @Test
    fun 배송에_실패한_주문에_배송을_다시_등록하면_행은_하나로_남고_새_운송장으로_다시_준비한다() {
        val orderId = savePaidOrder()
        val shippingId = saveShipping(orderId)
        shippingCommandService.updateShippingStatus(SELLER_ID, shippingId, ShippingStatusUpdateRequest(ShippingStatus.IN_TRANSIT))
        shippingCommandService.updateShippingStatus(SELLER_ID, shippingId, ShippingStatusUpdateRequest(ShippingStatus.FAILED))
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID.name)

        val restarted = shippingCommandService.createShipping(
            SELLER_ID,
            ShippingCreateRequest(
                orderId = orderId, carrierName = "한진택배", trackingNumber = "TRK-RETRY-$orderId",
                recipientName = "홍길동", recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
            )
        )

        assertThat(restarted.id).isEqualTo(shippingId)
        assertThat(
            jdbcTemplate.queryForList(
                "SELECT status, tracking_number, attempt FROM shippings WHERE order_id = ?", orderId
            ).map { listOf(it["status"], it["tracking_number"], (it["attempt"] as Number).toInt()) }
        ).containsExactly(listOf(ShippingStatus.PREPARING.name, "TRK-RETRY-$orderId", 2))
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID.name)
    }

    @Test
    fun 시도_번호_없이_들어간_옛_배송_행은_첫_시도로_읽힌다() {
        val orderId = savePaidOrder()
        jdbcTemplate.update(
            """
            INSERT INTO shippings (order_id, buyer_id, status, carrier_name, tracking_number,
                recipient_name, recipient_phone, recipient_address, created_at, updated_at)
            VALUES (?, ?, 'PREPARING', 'CJ대한통운', 'TRK-OLD', '홍길동', '010-1234-5678', '서울시 강남구', NOW(6), NOW(6))
            """.trimIndent(),
            orderId, BUYER_ID
        )

        assertThat(shippingRepository.findByOrderId(orderId)!!.attempt).isEqualTo(1)
    }

    @Test
    fun 주문_취소와_배송_시작이_겹치면_둘_다_주문_잠금에서_차례를_기다려_교착_없이_취소가_이긴다() {
        val orderId = savePaidOrder()
        val shippingId = saveShipping(orderId)
        val holderLocked = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
        val transactionTemplate = TransactionTemplate(transactionManager)
        val holder = executor.submit {
            transactionTemplate.executeWithoutResult {
                orderRepository.lockById(orderId)
                holderLocked.countDown()
                releaseHolder.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        }
        assertThat(holderLocked.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

        val enteredAt = AtomicLong()
        val cancel = executor.submit {
            enteredAt.set(System.currentTimeMillis())
            orderCommandService.cancelOrder(BUYER_ID, orderId)
        }
        awaitLockWait(cancel, enteredAt, "주문 취소", "orders")
        val shipment = executor.submit {
            shippingCommandService.updateShippingStatus(SELLER_ID, shippingId, ShippingStatusUpdateRequest(ShippingStatus.IN_TRANSIT))
        }
        val startedAt = System.currentTimeMillis()
        while (lockWaitCount("orders") < 2) {
            if (shipment.isDone || System.currentTimeMillis() - startedAt > LATCH_TIMEOUT_SECONDS * 1000) {
                throw AssertionError("배송 시작이 orders 잠금을 기다리지 않았다\n" + lockDiagnostics(shipment, enteredAt, startedAt))
            }
            Thread.onSpinWait()
        }
        releaseHolder.countDown()
        holder.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        cancel.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertThatThrownBy { shipment.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .hasCauseInstanceOf(ShippingInvalidStatusException::class.java)
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCEL_REQUESTED.name)
        assertThat(shippingStatus(shippingId)).isEqualTo(ShippingStatus.CANCELLED.name)
    }

    @Test
    fun 같은_주문에_배송_등록_둘이_동시에_오면_주문_잠금에서_차례를_기다려_하나만_만들고_다른_하나는_SHP002를_받는다() {
        val orderId = savePaidOrder()
        val enteredAt = AtomicLong()

        val requests = dataSource.connection.use { holder ->
            holder.autoCommit = false
            lockOrder(holder, orderId)
            val requests = listOf("TRK-A", "TRK-B").map { trackingNumber ->
                executor.submit<ShippingResponse> {
                    enteredAt.compareAndSet(0L, System.currentTimeMillis())
                    shippingCommandService.createShipping(SELLER_ID, request(orderId, trackingNumber))
                }
            }
            awaitAllWaiting(requests, enteredAt, "orders")
            holder.rollback()
            requests
        }

        val outcomes = requests.map { future ->
            try {
                Result.success(future.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            } catch (e: ExecutionException) {
                Result.failure(e.cause!!)
            }
        }
        assertThat(outcomes.count { it.isSuccess }).isEqualTo(1)
        assertThat(outcomes.mapNotNull { it.exceptionOrNull() }).singleElement()
            .isInstanceOf(ShippingAlreadyExistsException::class.java)
        assertThat(shippingCount(orderId)).isEqualTo(1)
    }

    @Test
    fun 다른_트랜잭션이_넣은_같은_주문의_배송이_커밋되면_그_행을_기다리던_등록은_500이_아니라_SHP002를_받는다() {
        val orderId = savePaidOrder()
        val enteredAt = AtomicLong()

        val pending = dataSource.connection.use { holder ->
            holder.autoCommit = false
            insertShipping(holder, orderId, "TRK-OTHER")
            val pending = executor.submit<ShippingResponse> {
                enteredAt.set(System.currentTimeMillis())
                shippingCommandService.createShipping(SELLER_ID, request(orderId, "TRK-A"))
            }
            awaitLockWait(pending, enteredAt, "배송 등록", "shippings")
            holder.commit()
            pending
        }

        assertThatThrownBy { pending.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .hasCauseInstanceOf(ShippingAlreadyExistsException::class.java)
        assertThat(shippingCount(orderId)).isEqualTo(1)
    }

    @Test
    fun 같은_주문의_배송_행은_DB가_둘째_행을_거절한다() {
        val orderId = savePaidOrder()
        dataSource.connection.use { insertShipping(it, orderId, "TRK-A") }

        assertThatThrownBy { dataSource.connection.use { insertShipping(it, orderId, "TRK-B") } }
            .hasMessageContaining("Duplicate entry")
        assertThat(shippingCount(orderId)).isEqualTo(1)
    }

    @Test
    fun 기존_배송이_있는_주문에_배송을_등록하면_SHP002를_받고_행이_늘지_않는다() {
        val orderId = savePaidOrder()
        shippingCommandService.createShipping(SELLER_ID, request(orderId, "TRK-A"))

        assertThatThrownBy { shippingCommandService.createShipping(SELLER_ID, request(orderId, "TRK-B")) }
            .isInstanceOf(ShippingAlreadyExistsException::class.java)
            .isNotInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(shippingCount(orderId)).isEqualTo(1)
    }

    private fun lockOrder(connection: Connection, orderId: Long) {
        connection.prepareStatement("SELECT id FROM orders WHERE id = ? FOR UPDATE").use {
            it.setLong(1, orderId)
            it.executeQuery().close()
        }
    }

    private fun insertShipping(connection: Connection, orderId: Long, trackingNumber: String) {
        connection.prepareStatement(
            """
            INSERT INTO shippings (order_id, buyer_id, status, carrier_name, tracking_number,
                recipient_name, recipient_phone, recipient_address, created_at, updated_at)
            VALUES (?, ?, 'PREPARING', 'CJ대한통운', ?, '홍길동', '010-1234-5678', '서울시 강남구', NOW(6), NOW(6))
            """.trimIndent()
        ).use {
            it.setLong(1, orderId)
            it.setLong(2, BUYER_ID)
            it.setString(3, trackingNumber)
            it.executeUpdate()
        }
    }

    private fun awaitAllWaiting(requests: List<Future<*>>, enteredAt: AtomicLong, waitTable: String) {
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + LATCH_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(waitTable) >= requests.size) return
            requests.firstOrNull { it.isDone }?.let {
                throw AssertionError("배송 등록이 $waitTable 잠금을 기다리지 않고 끝났다\n" + lockDiagnostics(it, enteredAt, startedAt))
            }
            Thread.onSpinWait()
        }
        throw AssertionError(
            "배송 등록 ${requests.size}개의 $waitTable 잠금 대기가 보이지 않았다\n" +
                requests.joinToString("\n") { lockDiagnostics(it, enteredAt, startedAt) }
        )
    }

    private fun request(orderId: Long, trackingNumber: String) = ShippingCreateRequest(
        orderId = orderId, carrierName = "CJ대한통운", trackingNumber = trackingNumber,
        recipientName = "홍길동", recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
    )

    private fun shippingCount(orderId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shippings WHERE order_id = ?", Int::class.java, orderId)!!

    private fun savePaidOrder(): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("5000.00"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        val orderId = orderRepository.save(order).id!!
        orderItemRepository.save(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = 100L,
                productName = "상품", productPrice = BigDecimal("5000.00"), quantity = 1
            )
        )
        return orderId
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
