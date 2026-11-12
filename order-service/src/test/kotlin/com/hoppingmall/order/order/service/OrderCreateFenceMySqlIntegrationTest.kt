package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.exception.OrderReservationFencedException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@Import(
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    OrderCreateFenceScenarios.TestConfig::class
)
abstract class OrderCreateFenceScenarios : MySqlJpaTestSupport() {

    @Autowired
    protected lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    protected lateinit var fence: ReservationFence

    @MockitoBean
    protected lateinit var cartItemRepository: CartItemRepository

    @MockitoBean
    protected lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    protected lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    protected lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    protected lateinit var orderMetrics: OrderMetrics

    @MockitoBean
    protected lateinit var transactionalEventPublisher: TransactionalEventPublisher

    protected fun stubCartOfTwoProducts() {
        whenever(cartItemRepository.findAllById(listOf(1L, 2L))).thenReturn(
            listOf(cartItem(id = 1L, productId = 100L, quantity = 2), cartItem(id = 2L, productId = 200L, quantity = 1))
        )
        whenever(productQueryPort.findProductsByIds(listOf(100L, 200L))).thenReturn(
            listOf(productInfo(100L), productInfo(200L))
        )
        whenever(inventoryCommandPort.batchReserveStock(listOf(100L to 2, 200L to 1)))
            .thenReturn(mapOf(100L to FIRST_RESERVATION, 200L to SECOND_RESERVATION))
    }

    protected fun createOrder() = orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L)))

    protected fun fenceRows(): List<Map<String, Any>> =
        jdbcTemplate.queryForList("SELECT reservation_id, decision FROM reservation_fence ORDER BY reservation_id")

    protected fun orderCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java)!!

    protected fun orderItemReservationIds(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM order_items ORDER BY product_id", String::class.java)

    private fun cartItem(id: Long, productId: Long, quantity: Int): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = productId,
            productName = "상품-$productId",
            productPrice = BigDecimal("25000"),
            productImageUrl = null,
            quantity = quantity
        )
        ReflectionTestUtils.setField(cartItem, "id", id)
        return cartItem
    }

    private fun productInfo(id: Long) = ProductInfo(id = id, name = "상품-$id", price = BigDecimal("25000"), sellerId = 5L)

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
            TransactionTemplate(transactionManager)
    }

    companion object {
        const val BUYER_ID = 10L
        const val FIRST_RESERVATION = "11111111-1111-1111-1111-111111111111"
        const val SECOND_RESERVATION = "22222222-2222-2222-2222-222222222222"
    }
}

@DisplayName("fence가 꺼진 주문 생성 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateFenceDisabledMySqlIntegrationTest : OrderCreateFenceScenarios() {

    @Test
    fun 플래그가_꺼져_있으면_주문_생성은_fence_테이블에_아무것도_쓰지_않는다() {
        stubCartOfTwoProducts()

        val response = createOrder()

        assertThat(fence.enabled).isFalse()
        assertThat(response.id).isNotNull()
        assertThat(orderCount()).isEqualTo(1)
        assertThat(orderItemReservationIds()).containsExactly(FIRST_RESERVATION, SECOND_RESERVATION)
        assertThat(fenceRows()).isEmpty()
    }

    @Test
    fun 플래그가_꺼져_있으면_조회가_먼저_ABORTED를_쓴_예약으로도_주문이_생성된다() {
        stubCartOfTwoProducts()
        fence.decide(SECOND_RESERVATION)

        createOrder()

        assertThat(orderCount()).isEqualTo(1)
        assertThat(fenceRows().map { it["decision"] }).containsExactly(ReservationFence.ABORTED)
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }
}

@DisplayName("fence가 켜진 주문 생성 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["order.reservation.fence.enabled=true"])
class OrderCreateFenceEnabledMySqlIntegrationTest : OrderCreateFenceScenarios() {

    @Test
    fun 주문이_커밋되면_예약마다_LINKED_행이_하나씩_함께_남는다() {
        stubCartOfTwoProducts()

        createOrder()

        assertThat(orderCount()).isEqualTo(1)
        assertThat(orderItemReservationIds()).containsExactly(FIRST_RESERVATION, SECOND_RESERVATION)
        assertThat(fenceRows().map { it["reservation_id"] to it["decision"] }).containsExactly(
            FIRST_RESERVATION to ReservationFence.LINKED,
            SECOND_RESERVATION to ReservationFence.LINKED
        )
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 예약_하나가_이미_ABORTED면_주문은_ORD009로_끝나고_주문과_나머지_LINKED는_남지_않으며_예약을_모두_취소한다() {
        stubCartOfTwoProducts()
        fence.decide(SECOND_RESERVATION)

        assertThatThrownBy { createOrder() }
            .isInstanceOfSatisfying(OrderReservationFencedException::class.java) {
                assertThat(it.errorCode.code).isEqualTo("ORD009")
            }

        assertThat(orderCount()).isZero()
        assertThat(orderItemReservationIds()).isEmpty()
        assertThat(fenceRows().map { it["reservation_id"] to it["decision"] }).containsExactly(
            SECOND_RESERVATION to ReservationFence.ABORTED
        )
        verify(inventoryCommandPort).cancelReservations(listOf(FIRST_RESERVATION, SECOND_RESERVATION))
    }
}
