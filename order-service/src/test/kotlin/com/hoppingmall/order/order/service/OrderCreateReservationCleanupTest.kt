package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("주문 저장 실패 뒤 예약 정리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCreateReservationCleanupTest {

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

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        service = OrderCommandServiceImpl(
            orderRepository, orderItemRepository, cartItemRepository,
            productQueryPort, inventoryCommandPort, paymentCommandPort,
            transactionalEventPublisher, orderMetrics, transactionTemplate, refundRepository
        )
        whenever(cartItemRepository.findAllById(listOf(1L, 2L))).thenReturn(
            listOf(cartItem(id = 1L, productId = 100L, quantity = 1), cartItem(id = 2L, productId = 200L, quantity = 2))
        )
        whenever(productQueryPort.findProductsByIds(listOf(100L, 200L))).thenReturn(
            listOf(productInfo(100L), productInfo(200L))
        )
        whenever(inventoryCommandPort.batchReserveStock(listOf(100L to 1, 200L to 2)))
            .thenReturn(mapOf(100L to "rsv-1", 200L to "rsv-2"))
        whenever(orderRepository.save(any<Order>())).thenAnswer {
            val order = it.arguments[0] as Order
            ReflectionTestUtils.setField(order, "id", 1L)
            order
        }
    }

    @Test
    fun 예약_성공_뒤_주문_항목_저장이_실패하면_받은_예약을_한_번_취소하고_원래_오류를_던진다() {
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-1", "rsv-2"))
        verify(cartItemRepository, never()).deleteAllById(any())
        verify(orderMetrics, never()).recordOrderCreated(any())
    }

    @Test
    fun 예약_취소_호출이_실패해도_주문_생성의_원래_오류를_그대로_던진다() {
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)
        whenever(inventoryCommandPort.cancelReservations(any())).thenThrow(RuntimeException("재고 서비스 연결 실패"))

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-1", "rsv-2"))
    }

    @Test
    fun 주문_저장이_성공하면_예약을_취소하지_않고_생성_지표를_한_번_기록한다() {
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val items = it.arguments[0] as List<OrderItem>
            items.forEachIndexed { index, item -> ReflectionTestUtils.setField(item, "id", (index + 1).toLong()) }
            items
        }

        service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L)))

        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(orderMetrics, times(1)).recordOrderCreated(BigDecimal("75000"))
    }

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

    companion object {
        private const val BUYER_ID = 10L
    }
}
