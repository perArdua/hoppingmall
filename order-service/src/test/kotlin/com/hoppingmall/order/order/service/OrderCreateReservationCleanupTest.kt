package com.hoppingmall.order.order.service

import com.hoppingmall.idempotency.IdempotencyRequestContext
import com.hoppingmall.idempotency.IdempotencyTxRecorder
import com.hoppingmall.idempotency.PendingIdempotency
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.exception.OrderCartChangedException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
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
import org.mockito.kotlin.argThat
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

    @AfterEach
    fun clearRequestContext() {
        IdempotencyRequestContext.clear()
    }

    @Test
    fun 예약_성공_뒤_주문_항목_저장이_실패하면_받은_예약을_한_번_취소하고_원래_오류를_던진다() {
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-1", "rsv-2"))
        verify(cartItemRepository, never()).deleteUnchanged(any(), any(), any())
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
        whenever(cartItemRepository.deleteUnchanged(any(), any(), any())).thenReturn(1)

        service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L)))

        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(orderMetrics, times(1)).recordOrderCreated(BigDecimal("75000"))
    }

    @Test
    fun 같은_멱등_키_주문이_먼저_커밋됐으면_공유된_예약을_취소하지_않고_원래_오류를_던진다() {
        val recorder: IdempotencyTxRecorder = mock()
        service.idempotencyTxRecorder = recorder
        val failure = DataIntegrityViolationException("idempotency_records 중복")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)
        whenever(recorder.claimedByCommittedRequest()).thenReturn(true)

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 먼저_커밋된_같은_키_주문이_없으면_예전처럼_예약을_취소한다() {
        val recorder: IdempotencyTxRecorder = mock()
        service.idempotencyTxRecorder = recorder
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)
        whenever(recorder.claimedByCommittedRequest()).thenReturn(false)

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-1", "rsv-2"))
    }

    @Test
    fun 주문_저장이_성공하면_트랜잭션_안에서_멱등_기록기를_한_번_부른다() {
        val recorder: IdempotencyTxRecorder = mock()
        service.idempotencyTxRecorder = recorder
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val items = it.arguments[0] as List<OrderItem>
            items.forEachIndexed { index, item -> ReflectionTestUtils.setField(item, "id", (index + 1).toLong()) }
            items
        }
        whenever(cartItemRepository.deleteUnchanged(any(), any(), any())).thenReturn(1)

        service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L)))

        verify(recorder, times(1)).recordIfPending(any())
        verify(recorder, never()).claimedByCommittedRequest()
    }

    @Test
    fun 장바구니_차지에_실패하면_멱등_기록기를_부르지_않고_그_주문의_예약을_취소한다() {
        val recorder: IdempotencyTxRecorder = mock()
        service.idempotencyTxRecorder = recorder
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }
        whenever(cartItemRepository.deleteUnchanged(any(), any(), any())).thenReturn(0)

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isInstanceOf(OrderCartChangedException::class.java)

        verify(recorder, never()).recordIfPending(any())
        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-1", "rsv-2"))
    }

    @Test
    fun 커밋_선점_요청이면_트랜잭션_안에서_예약을_유지로_연결하고_실패하면_취소로_정해진_예약만_취소한다() {
        val fence = disabledFence()
        service.reservationFence = fence
        openClaimByCommitRequest()
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)
        whenever(fence.link(any())).thenReturn(emptyList())
        whenever(fence.abortForCancel(listOf("rsv-1", "rsv-2"))).thenReturn(listOf("rsv-2"))

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(fence).link(argThat<Collection<String>> { toList() == listOf("rsv-1", "rsv-2") })
        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-2"))
    }

    @Test
    fun 커밋_선점_요청의_예약이_모두_유지거나_결정_전이면_취소하지_않는다() {
        val fence = disabledFence()
        service.reservationFence = fence
        openClaimByCommitRequest()
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)
        whenever(fence.abortForCancel(any())).thenReturn(emptyList())

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 커밋_선점_요청의_취소_결정_기록이_실패하면_취소하지_않고_원래_오류를_던진다() {
        val fence = disabledFence()
        service.reservationFence = fence
        openClaimByCommitRequest()
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)
        whenever(fence.abortForCancel(any())).thenThrow(RuntimeException("order DB 연결 실패"))

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 커밋_선점_요청이_아니면_결정_펜스를_쓰지_않고_예전처럼_예약을_취소한다() {
        val fence = disabledFence()
        service.reservationFence = fence
        val failure = DataIntegrityViolationException("order_items 저장 실패")
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenThrow(failure)

        assertThatThrownBy { service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isSameAs(failure)

        verify(fence, never()).link(any())
        verify(fence, never()).abortForCancel(any())
        verify(inventoryCommandPort, times(1)).cancelReservations(listOf("rsv-1", "rsv-2"))
    }

    private fun disabledFence(): ReservationFence {
        val fence = mock<ReservationFence>()
        Mockito.lenient().`when`(fence.enabled).thenReturn(false)
        return fence
    }

    private fun openClaimByCommitRequest() {
        IdempotencyRequestContext.open(
            PendingIdempotency("scoped-key", "POST", "/api/v1/orders", "fingerprint", 24, claimByCommit = true)
        )
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
