package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.request.OrderExpectedPriceRequest
import com.hoppingmall.order.order.dto.response.OrderPriceChange
import com.hoppingmall.order.order.exception.OrderPriceChangedException
import com.hoppingmall.order.port.ProductInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("주문 생성의 기대 가격 확인 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateExpectedPriceMySqlIntegrationTest : OrderCreateFenceScenarios() {

    private val cartItems = ConcurrentHashMap<Long, CartItem>()

    private val prices = ConcurrentHashMap<Long, BigDecimal>()

    @BeforeEach
    fun setUp() {
        cartItems.clear()
        prices.clear()
        whenever(cartItemRepository.findAllById(any())).thenAnswer { invocation ->
            invocation.getArgument<Iterable<Long>>(0).mapNotNull { cartItems[it] }
        }
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Long>>(0).map { product(it, prices.getValue(it)) }
        }
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Pair<Long, Int>>>(0).associate { (productId, _) -> productId to "reservation-$productId" }
        }
    }

    @Test
    fun 화면이_본_가격이_지금_가격과_같으면_그_가격으로_주문한다() {
        cart(1L, BUYER_ID, FIRST_PRODUCT_ID, "10000.00", 2)
        prices[FIRST_PRODUCT_ID] = BigDecimal("12000.00")

        val order = orderCommandService.createOrder(BUYER_ID, request(1L to "12000"))

        assertThat(order.totalAmount).isEqualByComparingTo("24000.00")
        assertThat(order.items.single().productPrice).isEqualByComparingTo("12000.00")
    }

    @Test
    fun 가격이_오른_뒤_옛_가격으로_주문하면_409로_거절하고_예약을_잡지_않는다() {
        cart(1L, BUYER_ID, FIRST_PRODUCT_ID, "10000.00", 1)
        prices[FIRST_PRODUCT_ID] = BigDecimal("12000.00")

        assertThatThrownBy { orderCommandService.createOrder(BUYER_ID, request(1L to "10000.00")) }
            .isInstanceOfSatisfying(OrderPriceChangedException::class.java) {
                assertThat(it.errorCode.code).isEqualTo("ORD014")
                assertThat(it.errorCode.status).isEqualTo(HttpStatus.CONFLICT)
                assertThat(it.changes).containsExactly(
                    OrderPriceChange(1L, FIRST_PRODUCT_ID, BigDecimal("10000.00"), BigDecimal("12000.00"))
                )
            }

        verify(inventoryCommandPort, never()).batchReserveStock(any())
        verify(cartItemRepository, never()).deleteAllById(any())
        assertThat(orderCount()).isZero()
    }

    @Test
    fun 가격이_내린_뒤_옛_가격으로_주문해도_409로_알린다() {
        cart(1L, BUYER_ID, FIRST_PRODUCT_ID, "10000.00", 1)
        prices[FIRST_PRODUCT_ID] = BigDecimal("8000.00")

        assertThatThrownBy { orderCommandService.createOrder(BUYER_ID, request(1L to "10000")) }
            .isInstanceOfSatisfying(OrderPriceChangedException::class.java) {
                assertThat(it.changes.single().currentPrice).isEqualByComparingTo("8000.00")
            }

        assertThat(orderCount()).isZero()
    }

    @Test
    fun 여러_항목_중_가격이_바뀐_항목만_알려_준다() {
        cart(1L, BUYER_ID, FIRST_PRODUCT_ID, "10000.00", 1)
        cart(2L, BUYER_ID, SECOND_PRODUCT_ID, "5000.00", 1)
        prices[FIRST_PRODUCT_ID] = BigDecimal("10000.00")
        prices[SECOND_PRODUCT_ID] = BigDecimal("5500.00")

        assertThatThrownBy { orderCommandService.createOrder(BUYER_ID, request(1L to "10000", 2L to "5000")) }
            .isInstanceOfSatisfying(OrderPriceChangedException::class.java) {
                assertThat(it.changes.map { change -> change.cartItemId }).containsExactly(2L)
            }

        assertThat(orderCount()).isZero()
    }

    @Test
    fun 가격_변경과_동시에_들어온_주문은_각자_읽은_가격이_화면_가격과_같을_때만_그_가격으로_만들어진다() {
        val buyers = (1..CONCURRENT_ORDERS).map { BUYER_ID + it }
        buyers.forEach { buyerId -> cart(buyerId, buyerId, FIRST_PRODUCT_ID, OLD_PRICE.toPlainString(), 2) }
        val reads = AtomicInteger()
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            val price = if (reads.getAndIncrement() % 2 == 0) OLD_PRICE else NEW_PRICE
            invocation.getArgument<List<Long>>(0).map { product(it, price) }
        }
        val start = CountDownLatch(1)
        val rejected = ConcurrentHashMap.newKeySet<Long>()
        val futures = buyers.map { buyerId ->
            executor.submit {
                start.await()
                try {
                    orderCommandService.createOrder(buyerId, request(buyerId to OLD_PRICE.toPlainString()))
                } catch (e: OrderPriceChangedException) {
                    assertThat(e.changes.single().currentPrice).isEqualByComparingTo(NEW_PRICE)
                    rejected.add(buyerId)
                }
            }
        }
        start.countDown()
        futures.forEach { it.get(60, TimeUnit.SECONDS) }

        verify(productQueryPort, times(CONCURRENT_ORDERS)).findProductsByIds(any())
        assertThat(rejected).hasSize(CONCURRENT_ORDERS / 2)
        assertThat(orderCount()).isEqualTo(CONCURRENT_ORDERS / 2)
        assertThat(jdbcTemplate.queryForList("SELECT buyer_id FROM orders", Long::class.java))
            .doesNotContainAnyElementsOf(rejected)
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT product_price FROM order_items", BigDecimal::class.java))
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(OLD_PRICE)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders o WHERE o.total_amount <> (SELECT SUM(i.total_price) FROM order_items i WHERE i.order_id = o.id)",
                Int::class.java
            )
        ).isZero()
    }

    private fun cart(cartItemId: Long, buyerId: Long, productId: Long, price: String, quantity: Int) {
        val cartItem = CartItem.create(
            buyerId = buyerId,
            productId = productId,
            productName = "상품-$productId",
            productPrice = BigDecimal(price),
            productImageUrl = null,
            quantity = quantity
        )
        ReflectionTestUtils.setField(cartItem, "id", cartItemId)
        cartItems[cartItemId] = cartItem
    }

    private fun request(vararg expected: Pair<Long, String>) = OrderCreateRequest(
        cartItemIds = expected.map { it.first },
        expectedPrices = expected.map { (cartItemId, price) -> OrderExpectedPriceRequest(cartItemId, BigDecimal(price)) }
    )

    private fun product(productId: Long, price: BigDecimal) =
        ProductInfo(id = productId, name = "상품-$productId", price = price, sellerId = 5L)

    companion object {
        private const val FIRST_PRODUCT_ID = 100L
        private const val SECOND_PRODUCT_ID = 200L
        private const val CONCURRENT_ORDERS = 20
        private val OLD_PRICE = BigDecimal("10000.00")
        private val NEW_PRICE = BigDecimal("12000.00")
    }
}
