package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.IdempotencyRequestContext
import com.hoppingmall.idempotency.IdempotencyService
import com.hoppingmall.idempotency.IdempotencyTxRecorder
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.JpaConfig
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.controller.OrderController
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.shipping.service.ShippingCommandService
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.redisson.api.RedissonClient
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.TransientDataAccessResourceException
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@DisplayName("같은 멱등 키 요청이 함께 쓰는 예약, 커밋 선점 켬 (MySQL)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    JpaConfig::class,
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    IdempotencyService::class,
    IdempotencyTxRecorder::class,
    OrderCreateSameKeySharedReservationMySqlIntegrationTest.TestConfig::class
)
@ImportAutoConfiguration(JacksonAutoConfiguration::class)
class OrderCreateSameKeySharedReservationMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var idempotencyService: IdempotencyService

    @Autowired
    private lateinit var cartItemRepository: CartItemRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var fence: ReservationFence

    @MockitoSpyBean
    private lateinit var idempotencyTxRecorder: IdempotencyTxRecorder

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var orderMetrics: OrderMetrics

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    private val reservationsByRequestKey = ConcurrentHashMap<String, Map<Long, String>>()

    private val cancelledReservationIds = CopyOnWriteArrayList<String>()

    private val recordCalls = AtomicInteger()

    private val firstRecorded = CountDownLatch(1)

    private val releaseFirst = CountDownLatch(1)

    private val firstFinished = CountDownLatch(1)

    @BeforeEach
    fun setUpScenario() {
        jdbcTemplate.update("DELETE FROM cart_items")
        jdbcTemplate.update("DELETE FROM idempotency_records")
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID))).thenReturn(
            listOf(ProductInfo(id = PRODUCT_ID, name = "상품-$PRODUCT_ID", price = PRICE, sellerId = SELLER_ID))
        )
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            reserveByRequestKey(invocation.arguments[0] as List<Pair<Long, Int>>)
        }
        whenever(inventoryCommandPort.cancelReservations(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cancelled = invocation.arguments[0] as List<String>
            cancelledReservationIds.addAll(cancelled)
            reservationsByRequestKey.entries.removeIf { (_, reserved) -> reserved.values.any { it in cancelled } }
            Unit
        }
    }

    @Test
    fun 같은_키의_다른_요청이_취소를_먼저_커밋한_예약으로는_주문을_만들지_않고_409_ORD009를_주며_다시_보내면_새_예약으로_주문한다() {
        val firstReservation = AtomicReference<String>()
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val reserved = reserveByRequestKey(invocation.arguments[0] as List<Pair<Long, Int>>)
            if (firstReservation.compareAndSet(null, reserved.values.single())) {
                assertThat(fence.abortForCancel(reserved.values)).containsExactlyElementsOf(reserved.values)
            }
            reserved
        }
        val cartItemId = saveCartItem()
        val controller = controllerWithClaimByCommit()
        val key = "same-key-${UUID.randomUUID()}"

        val first = outcome(controller, key, cartItemId)
        val ordersAfterFirst = orderIds()
        val cartAfterFirst = cartItemRepository.count()
        val retried = outcome(controller, key, cartItemId)

        val orderId = orderIds().single()
        val secondReservation = orderItemReservationIds().single()
        assertThat(first).isEqualTo("409:ORD009")
        assertThat(ordersAfterFirst).isEmpty()
        assertThat(cartAfterFirst).isEqualTo(1)
        assertThat(cancelledReservationIds).containsExactly(firstReservation.get())
        assertThat(retried).isEqualTo("201:$orderId")
        assertThat(secondReservation).isNotEqualTo(firstReservation.get())
        assertThat(decisionOf(firstReservation.get())).isEqualTo(ReservationFence.ABORTED)
        assertThat(decisionOf(secondReservation)).isEqualTo(ReservationFence.LINKED)
    }

    private fun reserveByRequestKey(items: List<Pair<Long, Int>>): Map<Long, String> {
        val requestKey = IdempotencyRequestContext.claimKey() ?: UUID.randomUUID().toString()
        return reservationsByRequestKey.computeIfAbsent(requestKey) {
            items.associate { (productId, _) -> productId to UUID.randomUUID().toString() }
        }
    }

    private fun decisionOf(reservationId: String): String? = fence.decisionOf(reservationId)

    @Test
    fun 이긴_요청이_커밋하기_전에_늦은_요청이_잠금_대기_시간_초과로_실패해도_이긴_주문의_예약은_취소되지_않는다() {
        doAnswer { invocation ->
            invocation.callRealMethod()
            if (recordCalls.incrementAndGet() == 1) {
                firstRecorded.countDown()
                check(releaseFirst.await(30, TimeUnit.SECONDS))
            }
            Unit
        }.whenever(idempotencyTxRecorder).recordIfPending(any())
        val cartItemId = saveCartItem()
        val controller = controllerWithClaimByCommit()
        val key = "same-key-${UUID.randomUUID()}"

        val winner = executor.submit(Callable { outcome(controller, key, cartItemId) })
        assertThat(firstRecorded.await(30, TimeUnit.SECONDS)).isTrue()
        val late = executor.submit(Callable { withShortLockWait { outcome(controller, key, cartItemId) } })
        val lateOutcome = late.get(60, TimeUnit.SECONDS)
        releaseFirst.countDown()
        val winnerOutcome = winner.get(60, TimeUnit.SECONDS)

        val orderId = orderIds().single()
        assertThat(winnerOutcome).isEqualTo("201:$orderId")
        assertThat(lateOutcome).startsWith("500:")
        assertThat(orderItemReservationIds()).isNotEmpty
        assertThat(cancelledReservationIds).doesNotContainAnyElementsOf(orderItemReservationIds())
    }

    @Test
    fun 이긴_요청이_멱등_기록을_넣은_뒤_커밋_전에_실패하면_기다리던_늦은_요청의_주문이_취소된_예약을_들고_남지_않는다() {
        doAnswer { invocation ->
            if (recordCalls.incrementAndGet() == 1) {
                invocation.callRealMethod()
                firstRecorded.countDown()
                check(releaseFirst.await(30, TimeUnit.SECONDS))
                throw TransientDataAccessResourceException("커밋 전 DB 오류 흉내")
            }
            invocation.callRealMethod()
            check(firstFinished.await(30, TimeUnit.SECONDS))
            Unit
        }.whenever(idempotencyTxRecorder).recordIfPending(any())
        val cartItemId = saveCartItem()
        val controller = controllerWithClaimByCommit()
        val key = "same-key-${UUID.randomUUID()}"

        val winner = executor.submit(Callable {
            try {
                outcome(controller, key, cartItemId)
            } finally {
                firstFinished.countDown()
            }
        })
        assertThat(firstRecorded.await(30, TimeUnit.SECONDS)).isTrue()
        val lateEnteredAt = AtomicLong()
        val late = executor.submit(Callable {
            lateEnteredAt.set(System.currentTimeMillis())
            outcome(controller, key, cartItemId)
        })
        awaitLateRequestLockWait(late, lateEnteredAt)
        releaseFirst.countDown()
        val winnerOutcome = winner.get(60, TimeUnit.SECONDS)
        val lateOutcome = late.get(60, TimeUnit.SECONDS)

        val orderId = orderIds().single()
        assertThat(winnerOutcome).startsWith("500:")
        assertThat(lateOutcome).isEqualTo("201:$orderId")
        assertThat(orderItemReservationIds()).isNotEmpty
        assertThat(cancelledReservationIds).doesNotContainAnyElementsOf(orderItemReservationIds())
    }

    private fun <T> withShortLockWait(block: () -> T): T {
        SHORT_LOCK_WAIT.set(true)
        return try {
            block()
        } finally {
            SHORT_LOCK_WAIT.remove()
        }
    }

    private fun awaitLateRequestLockWait(late: Future<*>, enteredAt: AtomicLong) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < LOCK_WAIT_LIMIT_MS) {
            if (LATE_REQUEST_WAIT_TABLES.any { lockWaitCount(it) > 0 }) return
            if (late.isDone) throw AssertionError("늦은 요청이 잠금에 걸리지 않고 끝났다\n" + lockDiagnostics(late, enteredAt, startedAt))
            Thread.onSpinWait()
        }
        throw AssertionError("늦은 요청의 잠금 대기가 보이지 않았다\n" + lockDiagnostics(late, enteredAt, startedAt))
    }

    private fun saveCartItem(): Long =
        cartItemRepository.save(
            CartItem.create(
                buyerId = BUYER_ID,
                productId = PRODUCT_ID,
                productName = "상품-$PRODUCT_ID",
                productPrice = PRICE,
                productImageUrl = null,
                quantity = 1
            )
        ).id!!

    private fun orderIds(): List<Long> = jdbcTemplate.queryForList("SELECT id FROM orders", Long::class.java)

    private fun orderItemReservationIds(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM order_items", String::class.java)

    private fun controllerWithClaimByCommit(): OrderController {
        val aspect = IdempotencyAspect(idempotencyService, mock<RedissonClient>(), objectMapper)
        ReflectionTestUtils.setField(aspect, "saveInTx", true)
        ReflectionTestUtils.setField(aspect, "claimByCommit", true)
        val factory = AspectJProxyFactory(OrderController(orderCommandService, mock(), mock<ShippingCommandService>()))
        factory.isProxyTargetClass = true
        factory.addAspect(aspect)
        return factory.getProxy()
    }

    private fun outcome(controller: OrderController, key: String, cartItemId: Long): String {
        val request = MockHttpServletRequest("POST", "/api/v1/orders")
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, key)
        request.addHeader("X-User-Id", BUYER_ID.toString())
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return try {
            val response: ResponseEntity<*> = controller.createOrder(
                UserPrincipal.of(BUYER_ID, "BUYER"),
                OrderCreateRequest(cartItemIds = listOf(cartItemId))
            )
            "${response.statusCode.value()}:${orderId(response.body)}"
        } catch (e: BusinessException) {
            "${e.errorCode.status.value()}:${e.errorCode.code}"
        } catch (e: Exception) {
            "500:${e.javaClass.simpleName}"
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    private fun orderId(body: Any?): Long? = when (body) {
        is ApiResponse<*> -> (body.data as OrderResponse).id
        is Map<*, *> -> ((body["data"] as Map<*, *>)["id"] as Number).toLong()
        else -> null
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun transactionTemplate(transactionManager: PlatformTransactionManager, jdbcTemplate: JdbcTemplate): TransactionTemplate =
            ShortLockWaitTransactionTemplate(transactionManager, jdbcTemplate)
    }

    class ShortLockWaitTransactionTemplate(
        transactionManager: PlatformTransactionManager,
        private val jdbcTemplate: JdbcTemplate
    ) : TransactionTemplate(transactionManager) {

        override fun <T> execute(action: TransactionCallback<T>): T? =
            super.execute(TransactionCallback<T> { status ->
                if (SHORT_LOCK_WAIT.get() != true) {
                    action.doInTransaction(status)
                } else {
                    jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout = 1")
                    try {
                        action.doInTransaction(status)
                    } finally {
                        runCatching { jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout = DEFAULT") }
                    }
                }
            })
    }

    companion object {
        private val SHORT_LOCK_WAIT = ThreadLocal<Boolean>()
        private val LATE_REQUEST_WAIT_TABLES = listOf("reservation_fence", "cart_items", "idempotency_records")
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val PRODUCT_ID = 100L
        private val PRICE = BigDecimal("25000.00")
    }
}
