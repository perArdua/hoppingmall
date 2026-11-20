package com.hoppingmall.order.order.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("자기 상품 주문 막기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCreateSelfPurchaseTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var paymentCommandPort: PaymentCommandPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var orderMetrics: OrderMetrics

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var refundRepository: RefundRepository

    private lateinit var service: OrderCommandServiceImpl

    private val handler = GlobalExceptionHandler()

    @BeforeEach
    fun setUp() {
        service = OrderCommandServiceImpl(
            orderRepository, orderItemRepository, cartItemRepository,
            productQueryPort, inventoryCommandPort, paymentCommandPort,
            transactionalEventPublisher, orderMetrics, transactionTemplate, refundRepository
        )
    }

    @Test
    fun 자기_상품만_담은_주문은_403_ORD015로_거절하고_예약도_주문도_만들지_않는다() {
        whenever(cartItemRepository.findAllById(listOf(OWN_CART_ITEM_ID))).thenReturn(listOf(cartItem(OWN_CART_ITEM_ID, OWN_PRODUCT_ID)))
        whenever(productQueryPort.findProductsByIds(listOf(OWN_PRODUCT_ID))).thenReturn(listOf(product(OWN_PRODUCT_ID, BUYER_ID)))

        assertSelfPurchaseRejected(listOf(OWN_CART_ITEM_ID))
    }

    @Test
    fun 남의_상품과_자기_상품이_섞여도_주문_전체를_거절한다() {
        whenever(cartItemRepository.findAllById(listOf(OTHER_CART_ITEM_ID, OWN_CART_ITEM_ID))).thenReturn(
            listOf(cartItem(OTHER_CART_ITEM_ID, OTHER_PRODUCT_ID), cartItem(OWN_CART_ITEM_ID, OWN_PRODUCT_ID))
        )
        whenever(productQueryPort.findProductsByIds(listOf(OTHER_PRODUCT_ID, OWN_PRODUCT_ID))).thenReturn(
            listOf(product(OTHER_PRODUCT_ID, OTHER_SELLER_ID), product(OWN_PRODUCT_ID, BUYER_ID))
        )

        assertSelfPurchaseRejected(listOf(OTHER_CART_ITEM_ID, OWN_CART_ITEM_ID))
    }

    private fun assertSelfPurchaseRejected(cartItemIds: List<Long>) {
        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = cartItemIds)) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
                assertThat(response.body?.code).isEqualTo("ORD015")
            }

        verify(inventoryCommandPort, never()).batchReserveStock(any())
        verify(orderRepository, never()).save(any<Order>())
        verify(cartItemRepository, never()).deleteUnchanged(any(), any(), any())
    }

    private fun cartItem(id: Long, productId: Long): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = productId,
            productName = "상품",
            productPrice = BigDecimal("1000"),
            productImageUrl = null,
            quantity = 1
        )
        ReflectionTestUtils.setField(cartItem, "id", id)
        return cartItem
    }

    private fun product(id: Long, sellerId: Long) =
        ProductInfo(id = id, name = "상품", price = BigDecimal("1000"), sellerId = sellerId)

    companion object {
        private const val BUYER_ID = 10L
        private const val OTHER_SELLER_ID = 20L
        private const val OWN_CART_ITEM_ID = 1L
        private const val OTHER_CART_ITEM_ID = 2L
        private const val OWN_PRODUCT_ID = 100L
        private const val OTHER_PRODUCT_ID = 200L
    }
}
