package com.hoppingmall.order.refund.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.domain.repository.RefundStatusQuantityProjection
import com.hoppingmall.order.refund.domain.repository.RefundedQuantityProjection
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.enum.ShippingStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("진행 중인 환불이 있는 항목의 중복 환불 요청")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundInProgressDuplicateTest {

    @Mock
    private lateinit var refundRepository: RefundRepository

    @Mock
    private lateinit var refundItemRepository: RefundItemRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var paymentQueryPort: PaymentQueryPort

    @Mock
    private lateinit var shippingRepository: ShippingRepository

    @Mock
    private lateinit var refundEventPublisher: RefundEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var service: RefundCommandServiceImpl

    private val handler = GlobalExceptionHandler()

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        Mockito.lenient().`when`(paymentQueryPort.findByOrderId(ORDER_ID)).thenReturn(
            PaymentInfo(
                id = 20L, orderId = ORDER_ID, amount = BigDecimal("40000"),
                pointAmount = BigDecimal.ZERO, couponId = null, status = "SUCCESS"
            )
        )
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("40000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            .forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        Mockito.lenient().`when`(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID))
            .thenReturn(listOf(orderItem(ITEM_A, quantity = 3), orderItem(ITEM_B, quantity = 1)))
        Mockito.lenient().`when`(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(deliveredShipping())
        Mockito.lenient().`when`(refundRepository.save(any<Refund>())).thenAnswer { invocation ->
            (invocation.arguments[0] as Refund).also { ReflectionTestUtils.setField(it, "id", REFUND_ID) }
        }
        Mockito.lenient().`when`(refundItemRepository.saveAll(any<List<RefundItem>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as List<RefundItem>).onEachIndexed { index, item ->
                ReflectionTestUtils.setField(item, "id", REFUND_ID + index)
            }
        }
        service = RefundCommandServiceImpl(
            refundRepository, refundItemRepository, orderRepository,
            orderItemRepository, paymentQueryPort, shippingRepository,
            refundEventPublisher, transactionTemplate
        )
    }

    @Test
    fun 판매자_확인을_기다리는_환불이_있는_항목을_다시_요청하면_409_RFD004로_거절하고_저장하지_않는다() {
        givenRefunded(ITEM_A, RefundStatus.REQUESTED, quantity = 1)

        assertConflict(ITEM_A)
    }

    @Test
    fun 승인된_뒤_아직_완료되지_않은_환불도_진행_중으로_본다() {
        givenRefunded(ITEM_A, RefundStatus.APPROVED, quantity = 1)

        assertConflict(ITEM_A)
    }

    @Test
    fun 다른_항목에만_진행_중인_환불이_있으면_이_항목의_환불은_받는다() {
        givenRefunded(ITEM_B, RefundStatus.REQUESTED, quantity = 1)

        val response = service.requestRefund(BUYER_ID, request(ITEM_A))

        assertThat(response.status).isEqualTo(RefundStatus.REQUESTED)
    }

    @Test
    fun 완료된_환불만_있으면_남은_수량의_환불은_받는다() {
        givenRefunded(ITEM_A, RefundStatus.COMPLETED, quantity = 1)

        val response = service.requestRefund(BUYER_ID, request(ITEM_A))

        assertThat(response.status).isEqualTo(RefundStatus.REQUESTED)
    }

    @Test
    fun 환불_요청은_주문_행을_잠근_뒤에_진행_중인_환불과_환불된_수량을_읽는다() {
        Mockito.lenient().`when`(refundItemRepository.findRefundQuantitiesByStatus(ORDER_ID)).thenReturn(emptyList())
        Mockito.lenient().`when`(refundItemRepository.findRefundedQuantitiesByOrderId(ORDER_ID)).thenReturn(emptyList())

        service.requestRefund(BUYER_ID, request(ITEM_A))

        val order = inOrder(orderRepository, refundItemRepository)
        order.verify(orderRepository).lockById(ORDER_ID)
        order.verify(refundItemRepository).findRefundQuantitiesByStatus(ORDER_ID)
        order.verify(refundItemRepository).findRefundedQuantitiesByOrderId(ORDER_ID)
    }

    private fun assertConflict(orderItemId: Long) {
        assertThatThrownBy { service.requestRefund(BUYER_ID, request(orderItemId)) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
                assertThat(response.body?.code).isEqualTo("RFD004")
            }
        verify(refundRepository, never()).save(any<Refund>())
        verify(refundItemRepository, never()).saveAll(any<List<RefundItem>>())
    }

    private fun givenRefunded(orderItemId: Long, status: RefundStatus, quantity: Long) {
        Mockito.lenient().`when`(refundItemRepository.findRefundQuantitiesByStatus(ORDER_ID)).thenReturn(
            listOf(statusQuantity(orderItemId, status, quantity))
        )
        Mockito.lenient().`when`(refundItemRepository.findRefundedQuantitiesByOrderId(ORDER_ID)).thenReturn(
            listOf(refundedQuantity(orderItemId, quantity))
        )
    }

    private fun statusQuantity(orderItemId: Long, status: RefundStatus, quantity: Long) =
        object : RefundStatusQuantityProjection {
            override val orderItemId: Long = orderItemId
            override val status: String = status.name
            override val quantity: Long = quantity
        }

    private fun refundedQuantity(orderItemId: Long, quantity: Long) =
        object : RefundedQuantityProjection {
            override val orderItemId: Long = orderItemId
            override val totalRefundedQuantity: Long = quantity
            override val totalRefundedAmount: BigDecimal = BigDecimal("10000").multiply(BigDecimal(quantity))
        }

    private fun orderItem(id: Long, quantity: Int): OrderItem {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = 5L, productId = 100L + id,
            productName = "상품$id", productPrice = BigDecimal("10000"), quantity = quantity
        )
        ReflectionTestUtils.setField(item, "id", id)
        return item
    }

    private fun deliveredShipping(): Shipping {
        val shipping = Shipping.create(
            orderId = ORDER_ID, buyerId = BUYER_ID, carrierName = "택배", trackingNumber = "T-1",
            recipientName = "구매자", recipientPhone = "010-0000-0000", recipientAddress = "주소"
        )
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(ShippingStatus.DELIVERED)
        return shipping
    }

    private fun request(orderItemId: Long) = RefundCreateRequest(
        orderId = ORDER_ID, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
        items = listOf(RefundItemRequest(orderItemId = orderItemId, quantity = 1))
    )

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val ITEM_A = 1L
        private const val ITEM_B = 2L
        private const val REFUND_ID = 30L
    }
}
