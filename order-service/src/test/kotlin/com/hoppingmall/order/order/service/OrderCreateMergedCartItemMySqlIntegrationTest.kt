package com.hoppingmall.order.order.service

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.IdempotencyRecord
import com.hoppingmall.idempotency.IdempotencyService
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.cartItem.service.CartItemCommandServiceImpl
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.request.OrderExpectedPriceRequest
import com.hoppingmall.order.order.controller.OrderController
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.exception.OrderEmptyItemsException
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.support.OrderMySqlServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

@DisplayName("동시에 담겨 합쳐진 장바구니 행을 두 주문이 가져가는 경우 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateMergedCartItemMySqlIntegrationTest : OrderMySqlServiceTestSupport() {

    @Autowired
    private lateinit var cartItemCommandService: CartItemCommandServiceImpl

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    private val reservedQuantities = ConcurrentLinkedQueue<Int>()

    private val cancelledReservations = ConcurrentLinkedQueue<String>()

    private val reservationSequence = AtomicInteger()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM cart_items")
        reservedQuantities.clear()
        cancelledReservations.clear()
        reservationSequence.set(0)
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product())
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID))).thenReturn(listOf(product()))
        whenever(inventoryCommandPort.cancelReservations(any())).thenAnswer { invocation ->
            cancelledReservations.addAll(invocation.getArgument<List<String>>(0))
            Unit
        }
    }

    @AfterEach
    fun resetRequest() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun 같은_상품을_동시에_두_번_담으면_두_응답이_같은_장바구니_행을_가리키고_수량은_2가_된다() {
        val (first, second) = addTwiceConcurrently()

        assertThat(first.id).isEqualTo(second.id)
        assertThat(cartRows()).containsExactly(listOf(first.id, 2L))
    }

    @Test
    fun 첫_주문이_끝난_뒤에_같은_장바구니_행으로_다시_주문하면_400으로_거절하고_재고를_다시_잡지_않는다() {
        stubReservationPassThrough()
        val (first, second) = addTwiceConcurrently()

        val firstOrder = orderCommandService.createOrder(BUYER_ID, request(first.id))

        assertThatThrownBy { orderCommandService.createOrder(BUYER_ID, request(second.id)) }
            .isInstanceOf(OrderEmptyItemsException::class.java)
        assertThat(firstOrder.items.single().quantity).isEqualTo(2)
        assertThat(orderRows()).containsExactly(listOf(2L, BigDecimal("2000.00")))
        assertThat(reservedQuantities).containsExactly(2)
        assertThat(cartRows()).isEmpty()
    }

    @Test
    fun 두_번째_주문이_장바구니를_읽은_뒤_첫_주문이_행을_지우면_두_번째_주문은_409로_거절하고_잡은_예약을_되돌린다() {
        val (first, second) = addTwiceConcurrently()
        val secondReserving = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            val sequence = reservationSequence.incrementAndGet()
            if (sequence == 1) {
                secondReserving.countDown()
                releaseSecond.await(LIMIT_SECONDS, TimeUnit.SECONDS)
            }
            reserve(invocation.getArgument(0), sequence)
        }

        val secondOrder = executor.submit<OrderResponse> {
            orderCommandService.createOrder(BUYER_ID, request(second.id))
        }
        assertThat(secondReserving.await(LIMIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        val firstOrder = orderCommandService.createOrder(BUYER_ID, request(first.id))
        assertThat(cartRows()).isEmpty()
        releaseSecond.countDown()
        val secondResult = outcome(secondOrder)

        assertThat(firstOrder.items.single().quantity).isEqualTo(2)
        assertCartChanged(secondResult.exceptionOrNull())
        assertThat(orderRows()).containsExactly(listOf(2L, BigDecimal("2000.00")))
        assertThat(reservedQuantities).containsExactlyInAnyOrder(2, 2)
        assertThat(cancelledReservations).containsExactly("reservation-1-$PRODUCT_ID")
        assertThat(orderItemReservations()).containsExactly("reservation-2-$PRODUCT_ID")
    }

    @Test
    fun 두_주문이_같은_행을_읽은_채_지우기에서_만나면_하나만_성공하고_다른_하나는_409로_거절하며_그_예약을_되돌린다() {
        stubReservationPassThrough()
        val (first, second) = addTwiceConcurrently()

        val outcomes = orderTogetherWhileCartRowLocked(first.id, second.id)

        val succeeded = outcomes.mapNotNull { it.getOrNull() }
        val failed = outcomes.mapNotNull { it.exceptionOrNull() }
        assertThat(succeeded).hasSize(1)
        assertThat(succeeded.single().items.single().quantity).isEqualTo(2)
        assertCartChanged(failed.single())
        assertThat(orderRows()).containsExactly(listOf(2L, BigDecimal("2000.00")))
        assertThat(reservedQuantities).containsExactlyInAnyOrder(2, 2)
        val kept = orderItemReservations().single()
        assertThat(cancelledReservations).containsExactlyElementsOf(listOf("reservation-1-$PRODUCT_ID", "reservation-2-$PRODUCT_ID") - kept)
        assertThat(cartRows()).isEmpty()
    }

    @Test
    fun 첫_주문이_수량_1을_읽은_뒤_담기가_합쳐져_수량_2가_되면_수량_1_주문은_409로_거절하고_예약을_되돌리며_수량_2_주문만_만들어진다() {
        val (firstOrder, secondOrder) = orderOneThenTwoAfterMergedAdd()

        assertCartChanged(firstOrder.exceptionOrNull())
        assertThat(secondOrder.getOrThrow().items.single().quantity).isEqualTo(2)
        assertThat(orderRows()).containsExactly(listOf(2L, BigDecimal("2000.00")))
        assertThat(reservedQuantities).containsExactly(1, 2)
        assertThat(cancelledReservations).containsExactly("reservation-1-$PRODUCT_ID")
        assertThat(orderItemReservations()).containsExactly("reservation-2-$PRODUCT_ID")
        assertThat(cartRows()).isEmpty()
    }

    @Test
    fun 주문이_장바구니를_읽은_뒤_그_행이_장바구니에서_삭제되면_409로_거절하고_예약을_되돌린다() {
        val cartItemId = add().id
        val reserving = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            reserving.countDown()
            release.await(LIMIT_SECONDS, TimeUnit.SECONDS)
            reserve(invocation.getArgument(0), reservationSequence.incrementAndGet())
        }

        val order = executor.submit<OrderResponse> { orderCommandService.createOrder(BUYER_ID, request(cartItemId)) }
        assertThat(reserving.await(LIMIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        cartItemCommandService.removeCartItem(BUYER_ID, cartItemId)
        release.countDown()

        assertCartChanged(outcome(order).exceptionOrNull())
        assertThat(orderRows()).isEmpty()
        assertThat(cancelledReservations).containsExactly("reservation-1-$PRODUCT_ID")
        assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_items WHERE id = ? AND deleted_at IS NOT NULL", Long::class.java, cartItemId)
        ).isEqualTo(1L)
    }

    @Test
    fun 같은_멱등_키로_주문을_다시_보내면_409가_아니라_첫_응답을_그대로_돌려주고_재고는_한_번만_잡는다() {
        stubReservationPassThrough()
        add()
        val cartItemId = add().id
        val controller = idempotentOrderController()
        val buyer = UserPrincipal.of(BUYER_ID, "BUYER")

        val first = withIdempotencyKey("order-key") { controller.createOrder(buyer, request(cartItemId)) }
        val replay = withIdempotencyKey("order-key") { controller.createOrder(buyer, request(cartItemId)) }

        assertThat(first.statusCode.value()).isEqualTo(201)
        assertThat(replay.statusCode.value()).isEqualTo(201)
        assertThat(replayMapper.writeValueAsString(replay.body)).isEqualTo(replayMapper.writeValueAsString(first.body))
        assertThat(orderRows()).containsExactly(listOf(2L, BigDecimal("2000.00")))
        assertThat(reservedQuantities).containsExactly(2)
        assertThat(cancelledReservations).isEmpty()
        assertThat(cartRows()).isEmpty()
    }

    private fun idempotentOrderController(): OrderController {
        val records = ConcurrentHashMap<String, IdempotencyRecord>()
        val idempotencyService: IdempotencyService = mock()
        val redissonClient: RedissonClient = mock()
        val lock: RLock = mock()
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(any(), any(), any())).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotencyService.findByKey(any())).thenAnswer { records[it.getArgument<String>(0)] }
        whenever(idempotencyService.save(any(), any(), any(), any(), any(), any(), any())).thenAnswer {
            val record = IdempotencyRecord(
                idempotencyKey = it.getArgument(0),
                httpMethod = it.getArgument(1),
                endpoint = it.getArgument(2),
                responseStatus = it.getArgument(3),
                responseBody = it.getArgument(4),
                expiresAt = LocalDateTime.now().plusHours(it.getArgument<Long>(6)),
                requestFingerprint = it.getArgument(5)
            )
            records[record.idempotencyKey] = record
            record
        }
        return AspectJProxyFactory(OrderController(orderCommandService, mock(), mock()))
            .apply {
                isProxyTargetClass = true
                addAspect(IdempotencyAspect(idempotencyService, redissonClient, replayMapper))
            }
            .getProxy()
    }

    private fun withIdempotencyKey(key: String, action: () -> ResponseEntity<*>): ResponseEntity<*> {
        val request = MockHttpServletRequest("POST", "/api/v1/orders")
        request.addHeader("X-User-Id", BUYER_ID.toString())
        request.addHeader("Idempotency-Key", key)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return action()
    }

    private fun orderOneThenTwoAfterMergedAdd(): Pair<Result<OrderResponse>, Result<OrderResponse>> {
        val reserving = listOf(CountDownLatch(1), CountDownLatch(1))
        val releases = listOf(CountDownLatch(1), CountDownLatch(1))
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            val sequence = reservationSequence.incrementAndGet()
            reserving[sequence - 1].countDown()
            releases[sequence - 1].await(LIMIT_SECONDS, TimeUnit.SECONDS)
            reserve(invocation.getArgument(0), sequence)
        }
        val cartItemId = add().id
        assertThat(cartRows()).containsExactly(listOf(cartItemId, 1L))

        val firstOrder = executor.submit<OrderResponse> { orderCommandService.createOrder(BUYER_ID, request(cartItemId)) }
        assertThat(reserving[0].await(LIMIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(add().id).isEqualTo(cartItemId)
        assertThat(cartRows()).containsExactly(listOf(cartItemId, 2L))

        val secondOrder = executor.submit<OrderResponse> { orderCommandService.createOrder(BUYER_ID, request(cartItemId)) }
        assertThat(reserving[1].await(LIMIT_SECONDS, TimeUnit.SECONDS)).isTrue()

        releases[0].countDown()
        val firstResult = outcome(firstOrder)
        releases[1].countDown()
        return firstResult to outcome(secondOrder)
    }

    private fun assertCartChanged(failure: Throwable?) {
        assertThat(failure).isInstanceOf(BusinessException::class.java)
        val errorCode = (failure as BusinessException).errorCode
        assertThat(errorCode.code).isEqualTo("ORD017")
        assertThat(errorCode.status).isEqualTo(HttpStatus.CONFLICT)
    }

    private fun outcome(future: Future<OrderResponse>): Result<OrderResponse> =
        try {
            Result.success(future.get(LIMIT_SECONDS, TimeUnit.SECONDS))
        } catch (e: ExecutionException) {
            Result.failure(e.cause!!)
        }

    private fun addTwiceConcurrently(): Pair<CartItemResponse, CartItemResponse> {
        val firstInserted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = executor.submit<CartItemResponse> {
            transactionTemplate.execute {
                val response = add()
                firstInserted.countDown()
                releaseFirst.await(LIMIT_SECONDS, TimeUnit.SECONDS)
                response
            }!!
        }
        assertThat(firstInserted.await(LIMIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        val enteredAt = AtomicLong()
        val second = executor.submit<CartItemResponse> {
            enteredAt.set(System.currentTimeMillis())
            add()
        }
        awaitLockWait(second, enteredAt, "두 번째 담기", CART_TABLE)
        releaseFirst.countDown()
        return first.get(LIMIT_SECONDS, TimeUnit.SECONDS) to second.get(LIMIT_SECONDS, TimeUnit.SECONDS)
    }

    private fun orderTogetherWhileCartRowLocked(vararg cartItemIds: Long): List<Result<OrderResponse>> {
        val pending = dataSource.connection.use { holder ->
            holder.autoCommit = false
            holder.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            holder.prepareStatement("SELECT id FROM cart_items WHERE id = ? FOR UPDATE").use {
                it.setLong(1, cartItemIds.first())
                it.executeQuery().close()
            }
            val orders = cartItemIds.map { cartItemId ->
                executor.submit<OrderResponse> { orderCommandService.createOrder(BUYER_ID, request(cartItemId)) }
            }
            awaitBothWaitingOnCartRow(orders)
            holder.rollback()
            orders
        }
        return pending.map { outcome(it) }
    }

    private fun awaitBothWaitingOnCartRow(orders: List<Future<OrderResponse>>) {
        val deadline = System.currentTimeMillis() + LIMIT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(CART_TABLE) >= orders.size) return
            if (orders.any { it.isDone }) {
                throw AssertionError("주문이 장바구니 행 잠금을 기다리지 않고 끝났다: " + orders.map { runCatching { it.get() } })
            }
            Thread.onSpinWait()
        }
        throw AssertionError("두 주문이 모두 장바구니 행 잠금을 기다리는 모습이 ${LIMIT_SECONDS}초 안에 보이지 않았다")
    }

    private fun stubReservationPassThrough() {
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            reserve(invocation.getArgument(0), reservationSequence.incrementAndGet())
        }
    }

    private fun reserve(items: List<Pair<Long, Int>>, sequence: Int): Map<Long, String> {
        items.forEach { (_, quantity) -> reservedQuantities.add(quantity) }
        return items.associate { (productId, _) -> productId to "reservation-$sequence-$productId" }
    }

    private fun add(): CartItemResponse =
        cartItemCommandService.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 1))

    private fun request(cartItemId: Long) =
        OrderCreateRequest(
            cartItemIds = listOf(cartItemId),
            expectedPrices = listOf(OrderExpectedPriceRequest(cartItemId = cartItemId, productPrice = PRICE))
        )

    private fun product() = ProductInfo(id = PRODUCT_ID, name = "상품", price = PRICE, sellerId = SELLER_ID)

    private fun cartRows(): List<List<Long>> =
        jdbcTemplate.query(
            "SELECT id, quantity FROM cart_items WHERE buyer_id = ? AND product_id = ? AND deleted_at IS NULL ORDER BY id",
            { rs, _ -> listOf(rs.getLong("id"), rs.getLong("quantity")) },
            BUYER_ID,
            PRODUCT_ID
        )

    private fun orderItemReservations(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM order_items ORDER BY id", String::class.java)

    private fun orderRows(): List<List<Any>> =
        jdbcTemplate.query(
            """
            SELECT SUM(oi.quantity) AS quantity, o.total_amount AS total_amount
            FROM orders o JOIN order_items oi ON oi.order_id = o.id
            WHERE o.buyer_id = ?
            GROUP BY o.id, o.total_amount
            ORDER BY o.id
            """.trimIndent(),
            { rs, _ -> listOf<Any>(rs.getLong("quantity"), rs.getBigDecimal("total_amount")) },
            BUYER_ID
        )

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val PRODUCT_ID = 100L
        private const val CART_TABLE = "cart_items"
        private const val LIMIT_SECONDS = 15L
        private val PRICE = BigDecimal("1000.00")
        private val replayMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    }
}
