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
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import io.grpc.Status
import io.grpc.StatusRuntimeException
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

@DisplayName("상품 서비스 연결 실패 시 주문 생성 응답")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCreateProductUnavailableTest {

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
        whenever(cartItemRepository.findAllById(listOf(CART_ITEM_ID))).thenReturn(listOf(cartItem()))
    }

    @Test
    fun 상품_목록_조회가_연결_실패면_상품_없음_404가_아니라_503으로_응답하고_예약을_잡지_않는다() {
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID)))
            .thenThrow(ProductQueryUnavailableException("상품 목록 조회 실패", StatusRuntimeException(Status.UNAVAILABLE)))

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(CART_ITEM_ID))) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                assertThat(response.body?.code).isNotEqualTo("ORD005")
            }

        verify(inventoryCommandPort, never()).batchReserveStock(any())
        verify(orderRepository, never()).save(any<Order>())
    }

    @Test
    fun 상품이_정말_없으면_여전히_404_ORD005로_응답한다() {
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID))).thenReturn(emptyList())

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(CART_ITEM_ID))) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                assertThat(response.body?.code).isEqualTo("ORD005")
            }

        verify(inventoryCommandPort, never()).batchReserveStock(any())
    }

    private fun cartItem(): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = PRODUCT_ID,
            productName = "상품",
            productPrice = BigDecimal("1000"),
            productImageUrl = null,
            quantity = 1
        )
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID)
        return cartItem
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val CART_ITEM_ID = 1L
        private const val PRODUCT_ID = 100L
    }
}
