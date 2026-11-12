package com.hoppingmall.order.cartItem.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

@DisplayName("장바구니 담기 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(CartItemCommandServiceImpl::class, CartItemAddMySqlIntegrationTest.TestConfig::class)
class CartItemAddMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var cartItemCommandService: CartItemCommandServiceImpl

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM cart_items")
        whenever(productQueryPort.findProductById(PRODUCT_ID))
            .thenReturn(ProductInfo(id = PRODUCT_ID, name = "상품", price = BigDecimal("1000"), sellerId = 5L))
    }

    @Test
    fun 같은_상품을_동시에_처음_담으면_한_행에_두_요청의_수량이_더해진다() {
        val firstInserted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = executor.submit<CartItemResponse> {
            transactionTemplate.execute {
                val response = add(2)
                firstInserted.countDown()
                releaseFirst.await()
                response
            }!!
        }
        assertThat(firstInserted.await(LIMIT_SECONDS, TimeUnit.SECONDS)).isTrue()

        val enteredAt = AtomicLong()
        val second = executor.submit<CartItemResponse> {
            enteredAt.set(System.currentTimeMillis())
            add(3)
        }
        awaitLockWaitOrDone(second, enteredAt)
        releaseFirst.countDown()

        first.get(LIMIT_SECONDS, TimeUnit.SECONDS)
        assertThat(second.get(LIMIT_SECONDS, TimeUnit.SECONDS).quantity).isEqualTo(5)
        assertThat(rows()).containsExactly(listOf(5, true))
    }

    @Test
    fun 이미_담긴_상품을_동시에_담으면_두_요청의_수량이_모두_더해진다() {
        add(1)
        val firstUpdated = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = executor.submit<CartItemResponse> {
            transactionTemplate.execute {
                val response = add(2)
                firstUpdated.countDown()
                releaseFirst.await()
                response
            }!!
        }
        assertThat(firstUpdated.await(LIMIT_SECONDS, TimeUnit.SECONDS)).isTrue()

        val enteredAt = AtomicLong()
        val second = executor.submit<CartItemResponse> {
            enteredAt.set(System.currentTimeMillis())
            add(3)
        }
        awaitLockWaitOrDone(second, enteredAt)
        releaseFirst.countDown()

        first.get(LIMIT_SECONDS, TimeUnit.SECONDS)
        second.get(LIMIT_SECONDS, TimeUnit.SECONDS)
        assertThat(rows()).containsExactly(listOf(6, true))
    }

    @Test
    fun 두_요청이_모두_빈_자리를_보고_넣으면_한_행만_남고_다른_요청은_다시_시도하라는_오류를_받는다() {
        val enteredAt = AtomicLong()
        val pending = dataSource.connection.use { holder ->
            holder.autoCommit = false
            holder.prepareStatement("SELECT id FROM cart_items WHERE buyer_id = ? AND product_id = ? FOR UPDATE").use {
                it.setLong(1, BUYER_ID)
                it.setLong(2, PRODUCT_ID)
                it.executeQuery().close()
            }
            val requests = listOf(2, 3).map { quantity ->
                executor.submit<CartItemResponse> {
                    enteredAt.compareAndSet(0L, System.currentTimeMillis())
                    add(quantity)
                }
            }
            awaitAllWaitingOrDone(requests, enteredAt)
            holder.rollback()
            requests
        }

        val outcomes = pending.map { future ->
            try {
                Result.success(future.get(LIMIT_SECONDS, TimeUnit.SECONDS))
            } catch (e: ExecutionException) {
                Result.failure(e.cause!!)
            }
        }
        val succeeded = outcomes.filter { it.isSuccess }.map { it.getOrThrow() }
        val failed = outcomes.mapNotNull { it.exceptionOrNull() }

        assertThat(succeeded).hasSize(1)
        assertThat(failed).hasSize(1)
        val errorCode = (failed.single() as BusinessException).errorCode
        assertThat(errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(errorCode.code).isEqualTo("C004")
        assertThat(rows()).containsExactly(listOf(succeeded.single().quantity, true))
    }

    @Test
    fun 삭제한_상품을_다시_담으면_새로_담은_수량만_남는다() {
        val removed = add(4)
        cartItemCommandService.removeCartItem(BUYER_ID, removed.id)

        val readded = add(1)

        assertThat(readded.quantity).isEqualTo(1)
        assertThat(activeQuantities()).containsExactly(1)
    }

    private fun add(quantity: Int): CartItemResponse =
        cartItemCommandService.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = quantity))

    private fun rows(): List<List<Any>> =
        jdbcTemplate.query(
            "SELECT quantity, deleted_at IS NULL AS active FROM cart_items WHERE buyer_id = ? AND product_id = ? ORDER BY id",
            { rs, _ -> listOf<Any>(rs.getInt("quantity"), rs.getBoolean("active")) },
            BUYER_ID,
            PRODUCT_ID
        )

    private fun activeQuantities(): List<Int> =
        jdbcTemplate.queryForList(
            "SELECT quantity FROM cart_items WHERE buyer_id = ? AND product_id = ? AND deleted_at IS NULL",
            Int::class.java,
            BUYER_ID,
            PRODUCT_ID
        )

    private fun awaitLockWaitOrDone(pending: Future<*>, enteredAt: AtomicLong) {
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + LIMIT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(TABLE) > 0 || pending.isDone) return
            Thread.onSpinWait()
        }
        throw AssertionError("두 번째 담기가 $TABLE 잠금 대기도 끝남도 보이지 않았다\n" + lockDiagnostics(pending, enteredAt, startedAt))
    }

    private fun awaitAllWaitingOrDone(requests: List<Future<*>>, enteredAt: AtomicLong) {
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + LIMIT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (waitingTransactionCount() + requests.count { it.isDone } >= requests.size) return
            Thread.onSpinWait()
        }
        throw AssertionError(
            "담기 ${requests.size}개가 모두 $TABLE 잠금 대기나 끝남에 이르지 않았다\n" +
                requests.joinToString("\n") { lockDiagnostics(it, enteredAt, startedAt) }
        )
    }

    private fun waitingTransactionCount(): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
            WHERE l.OBJECT_SCHEMA = DATABASE()
            AND l.OBJECT_NAME = ?
            """.trimIndent(),
            Long::class.java,
            TABLE
        )!!

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
            TransactionTemplate(transactionManager)
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val PRODUCT_ID = 100L
        private const val TABLE = "cart_items"
        private const val LIMIT_SECONDS = 15L
    }
}
