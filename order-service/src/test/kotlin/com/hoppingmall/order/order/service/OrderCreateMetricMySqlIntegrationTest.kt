package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("주문 생성 지표 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    OrderMetrics::class,
    OrderCreateMetricMySqlIntegrationTest.TestConfig::class
)
class OrderCreateMetricMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @MockitoBean
    private lateinit var cartItemRepository: CartItemRepository

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @AfterEach
    fun dropTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS $FAILING_TRIGGER")
    }

    @Test
    fun 주문_저장_트랜잭션이_커밋에서_실패하면_주문_생성_지표가_오르지_않는다() {
        stubCart()
        val countBefore = createdCount()
        val amountBefore = amountTotal()
        jdbcTemplate.execute(
            "CREATE TRIGGER $FAILING_TRIGGER BEFORE UPDATE ON orders FOR EACH ROW " +
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'commit failure for test'"
        )

        assertThatThrownBy { createOrder() }
            .hasStackTraceContaining("commit failure for test")

        assertThat(orderCount()).isZero()
        assertThat(orderItemCount()).isZero()
        assertThat(createdCount() - countBefore).isZero()
        assertThat(amountTotal() - amountBefore).isZero()
    }

    @Test
    fun 주문이_커밋되면_주문_생성_지표와_금액_합계가_오른다() {
        stubCart()
        val countBefore = createdCount()
        val amountBefore = amountTotal()

        createOrder()

        assertThat(orderCount()).isEqualTo(1)
        assertThat(createdCount() - countBefore).isEqualTo(1.0)
        assertThat(amountTotal() - amountBefore).isEqualTo(50_000.0)
    }

    private fun createOrder() = orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L)))

    private fun stubCart() {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = 100L,
            productName = "상품-100",
            productPrice = BigDecimal("25000"),
            productImageUrl = null,
            quantity = 2
        )
        ReflectionTestUtils.setField(cartItem, "id", 1L)
        whenever(cartItemRepository.findAllById(listOf(1L))).thenReturn(listOf(cartItem))
        whenever(productQueryPort.findProductsByIds(listOf(100L)))
            .thenReturn(listOf(ProductInfo(id = 100L, name = "상품-100", price = BigDecimal("25000"), sellerId = 5L)))
        whenever(inventoryCommandPort.batchReserveStock(listOf(100L to 2)))
            .thenReturn(mapOf(100L to "11111111-1111-1111-1111-111111111111"))
    }

    private fun createdCount(): Double = meterRegistry.counter("order.created.count").count()

    private fun amountTotal(): Double = meterRegistry.counter("order.amount.total").count()

    private fun orderCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java)!!

    private fun orderItemCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Int::class.java)!!

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
        private const val FAILING_TRIGGER = "fail_order_update_for_test"
    }
}
