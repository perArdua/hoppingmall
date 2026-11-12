package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.service.CartItemCommandServiceImpl
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderEmptyItemsException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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

@DisplayName("주문 생성의 찾을 수 없는 장바구니 id MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    CartItemCommandServiceImpl::class,
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    OrderMetrics::class,
    OrderCreateMissingCartItemMySqlIntegrationTest.TestConfig::class
)
class OrderCreateMissingCartItemMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var cartItemCommandService: CartItemCommandServiceImpl

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM cart_items")
        whenever(productQueryPort.findProductById(any())).thenAnswer { invocation -> product(invocation.getArgument(0)) }
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Long>>(0).map { product(it) }
        }
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Pair<Long, Int>>>(0).associate { (productId, _) -> productId to "reservation-$productId" }
        }
    }

    @Test
    fun 없는_장바구니_id가_섞이면_400으로_거절하고_주문과_예약을_만들지_않는다() {
        val keptId = add(BUYER_ID, KEPT_PRODUCT_ID, 1)

        assertThatThrownBy {
            orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(keptId, MISSING_CART_ITEM_ID)))
        }.isInstanceOfSatisfying(OrderEmptyItemsException::class.java) {
            assertThat(it.errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        assertThat(orderCount()).isZero()
        assertThat(liveCartItemIds()).containsExactly(keptId)
        verify(inventoryCommandPort, never()).batchReserveStock(any())
    }

    @Test
    fun 삭제한_장바구니_id가_섞이면_400으로_거절하고_남은_장바구니_행을_지우지_않는다() {
        val removedId = add(BUYER_ID, REMOVED_PRODUCT_ID, 2)
        cartItemCommandService.removeCartItem(BUYER_ID, removedId)
        val keptId = add(BUYER_ID, KEPT_PRODUCT_ID, 1)

        assertThatThrownBy {
            orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(removedId, keptId)))
        }.isInstanceOfSatisfying(OrderEmptyItemsException::class.java) {
            assertThat(it.errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        assertThat(orderCount()).isZero()
        assertThat(liveCartItemIds()).containsExactly(keptId)
        verify(inventoryCommandPort, never()).batchReserveStock(any())
    }

    @Test
    fun 같은_장바구니_id를_두_번_보내도_한_번만_주문한다() {
        val keptId = add(BUYER_ID, KEPT_PRODUCT_ID, 3)

        val order = orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(keptId, keptId)))

        assertThat(order.items.map { it.productId to it.quantity }).containsExactly(KEPT_PRODUCT_ID to 3)
        assertThat(orderCount()).isEqualTo(1)
    }

    @Test
    fun 다른_구매자의_장바구니_id가_섞이면_주문을_만들지_않는다() {
        val keptId = add(BUYER_ID, KEPT_PRODUCT_ID, 1)
        val otherId = add(OTHER_BUYER_ID, REMOVED_PRODUCT_ID, 1)

        assertThatThrownBy {
            orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(keptId, otherId)))
        }.isInstanceOf(OrderAccessDeniedException::class.java)

        assertThat(orderCount()).isZero()
        assertThat(liveCartItemIds()).containsExactlyInAnyOrder(keptId, otherId)
    }

    private fun add(buyerId: Long, productId: Long, quantity: Int): Long =
        cartItemCommandService.addCartItem(buyerId, CartItemCreateRequest(productId = productId, quantity = quantity)).id

    private fun product(productId: Long) =
        ProductInfo(id = productId, name = "상품-$productId", price = BigDecimal("1000"), sellerId = 5L)

    private fun orderCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java)!!

    private fun liveCartItemIds(): List<Long> =
        jdbcTemplate.queryForList("SELECT id FROM cart_items WHERE deleted_at IS NULL ORDER BY id", Long::class.java)

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
            TransactionTemplate(transactionManager)

        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val OTHER_BUYER_ID = 20L
        private const val REMOVED_PRODUCT_ID = 100L
        private const val KEPT_PRODUCT_ID = 200L
        private const val MISSING_CART_ITEM_ID = 999_999L
    }
}
