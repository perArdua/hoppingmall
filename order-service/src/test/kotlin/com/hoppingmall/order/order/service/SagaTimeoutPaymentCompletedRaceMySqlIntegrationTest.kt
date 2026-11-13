package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.dto.event.PaymentCompletedEvent
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("saga 시간 초과 보상과 결제 완료 처리가 겹칠 때 주문 상태와 재고 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SagaTimeoutPaymentCompletedRaceMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private val kafkaEventMapper: KafkaEventMapper = mock()

    private val sagaCompensationMetrics: SagaCompensationMetrics = mock()

    private val paymentQueryPort: PaymentQueryPort = mock()

    private lateinit var inventory: FakeInventory

    private lateinit var consumer: OrderSagaConsumer

    private lateinit var scheduler: SagaTimeoutScheduler

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
        inventory = FakeInventory()
        val transactionTemplate = TransactionTemplate(transactionManager)
        consumer = OrderSagaConsumer(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventory,
            transactionalEventPublisher,
            ObjectMapper(),
            kafkaEventMapper,
            transactionTemplate,
            sagaCompensationMetrics
        )
        scheduler = SagaTimeoutScheduler(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventory,
            paymentQueryPort,
            transactionalEventPublisher,
            transactionTemplate,
            sagaCompensationMetrics
        )
    }

    @Test
    fun 시간_초과로_취소된_주문에_결제_완료가_다시_오면_예약을_확정하지_않는다() {
        val round = prepareRound(1)

        scheduler.checkTimedOutSagas()
        consumer.handlePaymentCompleted(round.message)

        assertThat(orderStatus(round.orderId)).isEqualTo(OrderStatus.CANCELLED.name)
        assertThat(inventory.confirmCalls(round.reservationId)).isZero()
        assertThat(inventory.status(round.reservationId)).isEqualTo(ReservationState.CANCELLED)
        assertThat(inventory.available(round.productId)).isEqualTo(INITIAL_STOCK)
        assertThat(publishedCount("PaymentReversalRequested", round.orderId)).isEqualTo(1)
    }

    @Test
    fun 시간_초과_보상이_재고를_복원하는_사이에_결제_완료가_다시_와도_주문과_재고가_어긋나지_않는다() {
        val round = prepareRound(2)
        val restoreEntered = CountDownLatch(1)
        val releaseRestore = CountDownLatch(1)
        inventory.beforeCancelReservations = {
            restoreEntered.countDown()
            releaseRestore.await(AWAIT_SECONDS, TimeUnit.SECONDS)
        }

        val timeout = executor.submit { scheduler.checkTimedOutSagas() }
        assertThat(restoreEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        inventory.beforeCancelReservations = null

        consumer.handlePaymentCompleted(round.message)
        releaseRestore.countDown()
        timeout.get(AWAIT_SECONDS, TimeUnit.SECONDS)

        assertOneSideWon(round)
    }

    @Test
    fun 시간_초과_보상과_결제_완료가_같은_주문_잠금을_기다리다_동시에_풀려도_둘_중_하나만_이긴다() {
        repeat(CONCURRENT_ROUNDS) { index ->
            val round = prepareRound(10 + index)
            val lockAcquired = CountDownLatch(1)
            val releaseLock = CountDownLatch(1)
            val holder = executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    jdbcTemplate.queryForObject("SELECT id FROM orders WHERE id = ? FOR UPDATE", Long::class.java, round.orderId)
                    lockAcquired.countDown()
                    releaseLock.await(AWAIT_SECONDS, TimeUnit.SECONDS)
                }
            }
            assertThat(lockAcquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()

            val timeout = executor.submit { scheduler.checkTimedOutSagas() }
            val payment = executor.submit { consumer.handlePaymentCompleted(round.message) }
            awaitOrderLockWaiters(expected = 2, round)

            releaseLock.countDown()
            holder.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            timeout.get(AWAIT_SECONDS, TimeUnit.SECONDS)
            payment.get(AWAIT_SECONDS, TimeUnit.SECONDS)

            assertOneSideWon(round)
        }
    }

    private fun assertOneSideWon(round: Round) {
        val status = orderStatus(round.orderId)
        val reservation = inventory.status(round.reservationId)
        val available = inventory.available(round.productId)
        val reversals = publishedCount("PaymentReversalRequested", round.orderId)
        val state = "order=$status, reservation=$reservation, available=$available, reversals=$reversals"
        val paidAndConfirmed = status == OrderStatus.PAID.name &&
            reservation == ReservationState.CONFIRMED &&
            available == INITIAL_STOCK - QUANTITY &&
            reversals == 0
        val cancelledAndRestored = status == OrderStatus.CANCELLED.name &&
            reservation == ReservationState.CANCELLED &&
            available == INITIAL_STOCK &&
            reversals == 1
        assertThat(paidAndConfirmed || cancelledAndRestored).describedAs(state).isTrue()
    }

    private fun awaitOrderLockWaiters(expected: Long, round: Round) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount("orders") >= expected) return
            Thread.onSpinWait()
        }
        throw AssertionError(
            "주문 ${round.orderId} 잠금 대기가 ${expected}개가 되지 않았다: 지금 ${lockWaitCount("orders")}개, " +
                "reservation=${inventory.status(round.reservationId)}, confirmCalls=${inventory.confirmCalls(round.reservationId)}, " +
                "order=${orderStatus(round.orderId)}"
        )
    }

    private fun prepareRound(index: Int): Round {
        val productId = 1_000L + index
        val reservationId = "rsv-race-$index"
        val transactionId = "txn-race-$index"
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("30000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        val orderId = orderRepository.save(order).id!!
        orderItemRepository.save(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = productId,
                productName = "상품$index", productPrice = BigDecimal("10000"),
                quantity = QUANTITY, reservationId = reservationId
            )
        )
        inventory.reserve(reservationId, productId, QUANTITY)

        val createdAt = LocalDateTime.now().minusMinutes(10)
        val saga = SagaEventLog(
            eventId = "payment-completed-$transactionId",
            eventType = "PAYMENT_COMPLETED",
            orderId = orderId,
            paymentId = PAYMENT_ID + index,
            createdAt = createdAt,
            timeoutAt = createdAt.plusMinutes(SagaEventLog.DEFAULT_TIMEOUT_MINUTES)
        )
        saga.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        sagaEventLogRepository.save(saga)

        val message = "payment-completed-$index"
        whenever(kafkaEventMapper.readValue(eq(message), eq(PaymentCompletedEvent::class.java))).thenReturn(
            PaymentCompletedEvent(
                paymentId = PAYMENT_ID + index,
                orderId = orderId,
                userId = BUYER_ID,
                amount = BigDecimal("30000"),
                transactionId = transactionId
            )
        )
        return Round(orderId, productId, reservationId, message)
    }

    private fun publishedCount(eventType: String, orderId: Long): Int =
        mockingDetails(transactionalEventPublisher).invocations.count {
            it.method.name == "publishEvent" && it.arguments[2] == eventType && it.arguments[1] == orderId.toString()
        }

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    private data class Round(val orderId: Long, val productId: Long, val reservationId: String, val message: String)

    enum class ReservationState { RESERVED, CONFIRMED, CANCELLED }

    private class FakeInventory : InventoryCommandPort {
        private data class Reservation(val productId: Long, val quantity: Int, var state: ReservationState)

        private val reservations = mutableMapOf<String, Reservation>()
        private val available = mutableMapOf<Long, Int>()
        private val confirmCalls = mutableMapOf<String, Int>()

        @Volatile
        var beforeCancelReservations: (() -> Unit)? = null

        @Synchronized
        fun reserve(reservationId: String, productId: Long, quantity: Int) {
            reservations[reservationId] = Reservation(productId, quantity, ReservationState.RESERVED)
            available[productId] = INITIAL_STOCK - quantity
        }

        @Synchronized
        fun status(reservationId: String): ReservationState = reservations.getValue(reservationId).state

        @Synchronized
        fun available(productId: Long): Int = available.getValue(productId)

        @Synchronized
        fun confirmCalls(reservationId: String): Int = confirmCalls[reservationId] ?: 0

        @Synchronized
        override fun confirmReservations(reservationIds: List<String>): Boolean {
            reservationIds.forEach { confirmCalls[it] = (confirmCalls[it] ?: 0) + 1 }
            val targets = reservationIds.mapNotNull { reservations[it] }
            if (targets.size != reservationIds.size || targets.any { it.state == ReservationState.CANCELLED }) {
                throw IllegalStateException("INV004")
            }
            targets.forEach { it.state = ReservationState.CONFIRMED }
            return true
        }

        override fun cancelReservations(reservationIds: List<String>) {
            beforeCancelReservations?.invoke()
            release(reservationIds, ReservationState.RESERVED)
        }

        override fun cancelReservation(reservationId: String) {
            release(listOf(reservationId), ReservationState.RESERVED)
        }

        override fun cancelConfirmedReservations(reservationIds: List<String>) {
            release(reservationIds, ReservationState.CONFIRMED)
        }

        @Synchronized
        private fun release(reservationIds: List<String>, from: ReservationState) {
            reservationIds.mapNotNull { reservations[it] }.filter { it.state == from }.forEach {
                it.state = ReservationState.CANCELLED
                available[it.productId] = available.getValue(it.productId) + it.quantity
            }
        }

        override fun decreaseStock(productId: Long, quantity: Int) = throw UnsupportedOperationException()

        override fun increaseStock(productId: Long, quantity: Int) = throw UnsupportedOperationException()

        override fun reserveStock(productId: Long, quantity: Int): String = throw UnsupportedOperationException()

        override fun batchReserveStock(items: List<Pair<Long, Int>>): Map<Long, String> = throw UnsupportedOperationException()
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 20L
        private const val PAYMENT_ID = 500L
        private const val QUANTITY = 3
        private const val INITIAL_STOCK = 10
        private const val CONCURRENT_ROUNDS = 5
        private const val AWAIT_SECONDS = 20L
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
    }
}
