package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.service.CartItemCommandService
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.port.PaymentCancellationResult
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.support.OrderMySqlServiceTestSupport
import com.hoppingmall.order.support.seedFenceRows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("reservation_fence 정리와 주문 경로 동시 실행 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["order.reservation.fence.enabled=true"])
class ReservationFenceCleanupConcurrencyMySqlIntegrationTest : OrderMySqlServiceTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var fence: ReservationFence

    @Autowired
    private lateinit var cartItemCommandService: CartItemCommandService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun 정리가_도는_동안_주문_생성과_취소와_늦은_취소_결정이_교착_없이_끝난다() {
        val product = ProductInfo(id = PRODUCT_ID, name = "상품", price = PRICE, sellerId = SELLER_ID)
        whenever(productQueryPort.findProductsByIds(any())).thenReturn(listOf(product))
        whenever(productQueryPort.findProductById(any())).thenReturn(product)
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { mapOf(PRODUCT_ID to UUID.randomUUID().toString()) }
        whenever(paymentCommandPort.cancelPayment(any())).thenReturn(PaymentCancellationResult.CANCELLED)
        val unfinished = jdbcTemplate.seedFenceRows(UNFINISHED_ROWS, "LINKED", listOf("PAID"), hoursAgo = 72)
        jdbcTemplate.seedFenceRows(TARGET_ROWS / 2, "ABORTED", emptyList(), hoursAgo = 48)
        jdbcTemplate.seedFenceRows(TARGET_ROWS / 2, "LINKED", listOf("CANCELLED", "DELIVERED", "REFUNDED"), hoursAgo = 30)
        val cleaner = ReservationFenceCleanupScheduler(jdbcTemplate, transactionManager, true, 24, BATCH_SIZE, MAX_RUN_MS)
        val deadlocksBefore = deadlocks()

        val cleanup = executor.submit(Callable { cleaner.cleanUp() })
        val created = AtomicInteger()
        val createdWhileCleaning = AtomicInteger()
        val workers = List(WORKERS) { worker ->
            executor.submit(Callable {
                var round = 0
                while (!cleanup.isDone || round < MIN_ROUNDS) {
                    val buyerId = BUYER_ID + worker * BUYERS_PER_WORKER + round
                    val orderId = orderCommandService.createOrder(buyerId, OrderCreateRequest(listOf(newCartItem(buyerId)))).id
                    created.incrementAndGet()
                    orderCommandService.cancelOrder(buyerId, orderId)
                    assertThat(fence.abortForCancel(listOf(unfinished[round % unfinished.size]))).isEmpty()
                    if (!cleanup.isDone) createdWhileCleaning.incrementAndGet()
                    round++
                }
            })
        }

        assertThat(cleanup.get(120, TimeUnit.SECONDS)).isEqualTo(TARGET_ROWS)
        workers.forEach { it.get(120, TimeUnit.SECONDS) }
        assertThat(createdWhileCleaning.get()).isPositive()
        assertThat(deadlocks() - deadlocksBefore).isZero()
        assertThat(fenceIds()).hasSize(unfinished.size + created.get()).containsAll(unfinished)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE status = 'PAYING'", Int::class.java)).isZero()
    }

    private fun newCartItem(buyerId: Long): Long =
        cartItemCommandService.addCartItem(buyerId, CartItemCreateRequest(PRODUCT_ID, 1)).id

    private fun fenceIds(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM reservation_fence", String::class.java)

    private fun deadlocks(): Long =
        jdbcTemplate.queryForObject(
            "SELECT `COUNT` FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks'",
            Long::class.java
        )!!

    companion object {
        private const val TARGET_ROWS = 30_000
        private const val UNFINISHED_ROWS = 1_000
        private const val BATCH_SIZE = 200
        private const val MAX_RUN_MS = 120_000L
        private const val WORKERS = 3
        private const val MIN_ROUNDS = 5
        private const val BUYER_ID = 9_100L
        private const val BUYERS_PER_WORKER = 1_000_000L
        private const val SELLER_ID = 5L
        private const val PRODUCT_ID = 100L
        private val PRICE = BigDecimal("25000.00")
    }
}
