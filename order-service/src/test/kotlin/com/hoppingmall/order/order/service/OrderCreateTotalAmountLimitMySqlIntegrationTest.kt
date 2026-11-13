package com.hoppingmall.order.order.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.service.CartItemCommandServiceImpl
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
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

@DisplayName("주문 합계 저장 한도 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    CartItemCommandServiceImpl::class,
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    OrderMetrics::class,
    OrderCreateTotalAmountLimitMySqlIntegrationTest.TestConfig::class
)
class OrderCreateTotalAmountLimitMySqlIntegrationTest : MySqlJpaTestSupport() {

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

    private val prices = mutableMapOf<Long, BigDecimal>()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM cart_items")
        prices.clear()
        whenever(productQueryPort.findProductById(any())).thenAnswer { invocation -> product(invocation.getArgument(0)) }
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Long>>(0).map { product(it) }
        }
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Pair<Long, Int>>>(0).associate { (productId, _) -> productId to "reservation-$productId" }
        }
    }

    @Test
    fun 합계가_주문_합계_저장_한도를_넘으면_400으로_거절하고_예약을_잡지_않는다() {
        prices[FIRST_PRODUCT_ID] = BigDecimal("50000000.00")
        val cartItemId = add(FIRST_PRODUCT_ID, 2)

        assertThatThrownBy {
            orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(cartItemId)))
        }.isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        verify(inventoryCommandPort, never()).batchReserveStock(any())
        assertThat(orderCount()).isZero()
        assertThat(liveCartItemIds()).containsExactly(cartItemId)
    }

    @Test
    fun 여러_장바구니_행을_더한_합계가_한도를_넘어도_예약_전에_거절한다() {
        prices[FIRST_PRODUCT_ID] = BigDecimal("60000000.00")
        prices[SECOND_PRODUCT_ID] = BigDecimal("40000000.00")
        val firstId = add(FIRST_PRODUCT_ID, 1)
        val secondId = add(SECOND_PRODUCT_ID, 1)

        assertThatThrownBy {
            orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(firstId, secondId)))
        }.isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        verify(inventoryCommandPort, never()).batchReserveStock(any())
        assertThat(orderCount()).isZero()
    }

    @Test
    fun 합계가_한도와_같으면_주문을_만든다() {
        prices[FIRST_PRODUCT_ID] = BigDecimal("99999999.99")
        val cartItemId = add(FIRST_PRODUCT_ID, 1)

        val order = orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(cartItemId)))

        assertThat(order.totalAmount).isEqualByComparingTo(BigDecimal("99999999.99"))
        assertThat(orderCount()).isEqualTo(1)
    }

    private fun add(productId: Long, quantity: Int): Long =
        cartItemCommandService.addCartItem(BUYER_ID, CartItemCreateRequest(productId = productId, quantity = quantity)).id

    private fun product(productId: Long) =
        ProductInfo(id = productId, name = "상품-$productId", price = prices.getValue(productId), sellerId = 5L)

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
        private const val FIRST_PRODUCT_ID = 100L
        private const val SECOND_PRODUCT_ID = 200L
    }
}
