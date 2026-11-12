package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.exception.CartItemNotFoundException
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.exception.OrderEmptyItemsException
import com.hoppingmall.order.order.service.OrderCommandServiceImpl
import com.hoppingmall.order.order.service.ReservationFence
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
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("삭제한 장바구니 행 조회 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    CartItemCommandServiceImpl::class,
    CartItemQueryServiceImpl::class,
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    OrderMetrics::class,
    CartItemSoftDeleteMySqlIntegrationTest.TestConfig::class
)
class CartItemSoftDeleteMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var cartItemCommandService: CartItemCommandServiceImpl

    @Autowired
    private lateinit var cartItemQueryService: CartItemQueryServiceImpl

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var cartItemRepository: CartItemRepository

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
        listOf(REMOVED_PRODUCT_ID, KEPT_PRODUCT_ID).forEach { productId ->
            whenever(productQueryPort.findProductById(productId)).thenReturn(product(productId))
        }
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Long>>(0).map { product(it) }
        }
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Pair<Long, Int>>>(0).associate { (productId, _) -> productId to "reservation-$productId" }
        }
    }

    @Test
    fun 삭제한_장바구니_행은_목록에_나오지_않는다() {
        val removedId = addAndRemove()
        val keptId = add(KEPT_PRODUCT_ID, 1)

        val listed = cartItemQueryService.getCartItems(BUYER_ID, PageRequest.of(0, 10)).content.map { it.id }

        assertThat(listed).containsExactly(keptId)
        assertThat(listed).doesNotContain(removedId)
    }

    @Test
    fun 삭제한_장바구니_행은_id로_조회되지_않는다() {
        val removedId = addAndRemove()

        assertThat(cartItemRepository.findById(removedId)).isEmpty
        assertThat(cartItemRepository.findAllById(listOf(removedId))).isEmpty()
    }

    @Test
    fun 삭제한_장바구니_행의_수량은_바꿀_수_없다() {
        val removedId = addAndRemove()

        assertThatThrownBy {
            cartItemCommandService.updateCartItemQuantity(BUYER_ID, removedId, CartItemUpdateRequest(quantity = 5))
        }.isInstanceOf(CartItemNotFoundException::class.java)
        assertThat(quantityOf(removedId)).isEqualTo(2)
    }

    @Test
    fun 삭제한_장바구니_id로는_주문을_만들_수_없다() {
        val removedId = addAndRemove()

        assertThatThrownBy {
            orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(removedId)))
        }.isInstanceOf(OrderEmptyItemsException::class.java)
        assertThat(orderCount()).isZero()
    }

    @Test
    fun 삭제한_장바구니_행은_남은_행과_함께_주문해도_주문_항목에_들어가지_않는다() {
        val removedId = addAndRemove()
        val keptId = add(KEPT_PRODUCT_ID, 1)

        val order = orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(removedId, keptId)))

        assertThat(order.items.map { it.productId }).containsExactly(KEPT_PRODUCT_ID)
        assertThat(orderedProductIds()).containsExactly(KEPT_PRODUCT_ID)
    }

    private fun addAndRemove(): Long {
        val removedId = add(REMOVED_PRODUCT_ID, 2)
        cartItemCommandService.removeCartItem(BUYER_ID, removedId)
        return removedId
    }

    private fun add(productId: Long, quantity: Int): Long =
        cartItemCommandService.addCartItem(BUYER_ID, CartItemCreateRequest(productId = productId, quantity = quantity)).id

    private fun product(productId: Long) =
        ProductInfo(id = productId, name = "상품-$productId", price = BigDecimal("1000"), sellerId = 5L)

    private fun quantityOf(cartItemId: Long): Int =
        jdbcTemplate.queryForObject("SELECT quantity FROM cart_items WHERE id = ?", Int::class.java, cartItemId)!!

    private fun orderCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java)!!

    private fun orderedProductIds(): List<Long> =
        jdbcTemplate.queryForList("SELECT product_id FROM order_items ORDER BY id", Long::class.java)

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
        private const val REMOVED_PRODUCT_ID = 100L
        private const val KEPT_PRODUCT_ID = 200L
    }
}
