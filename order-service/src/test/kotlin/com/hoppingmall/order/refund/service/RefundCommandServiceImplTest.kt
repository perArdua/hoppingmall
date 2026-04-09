package com.hoppingmall.order.refund.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.domain.repository.RefundedQuantityProjection
import com.hoppingmall.order.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.exception.RefundAccessDeniedException
import com.hoppingmall.order.refund.exception.RefundException
import com.hoppingmall.order.refund.exception.RefundNotFoundException
import com.hoppingmall.order.refund.exception.RefundPaymentNotFoundException
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("RefundCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundCommandServiceImplTest {

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

    private val payment = PaymentInfo(
        id = 20L, orderId = 10L, amount = BigDecimal("50000"),
        pointAmount = BigDecimal("500"), couponId = null, status = "SUCCESS"
    )

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        service = RefundCommandServiceImpl(
            refundRepository, refundItemRepository, orderRepository,
            orderItemRepository, paymentQueryPort, shippingRepository,
            refundEventPublisher, transactionTemplate
        )
    }

    private fun createOrder(buyerId: Long = 1L, status: OrderStatus = OrderStatus.PAID): Order {
        val order = Order.create(buyerId = buyerId, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        if (status == OrderStatus.SHIPPED) {
            order.updateStatus(OrderStatus.SHIPPED)
        } else if (status == OrderStatus.DELIVERED) {
            order.updateStatus(OrderStatus.SHIPPED)
            order.updateStatus(OrderStatus.DELIVERED)
        }
        ReflectionTestUtils.setField(order, "id", 10L)
        return order
    }

    private fun createOrderItem(id: Long = 1L, quantity: Int = 2): OrderItem {
        val item = OrderItem.create(
            orderId = 10L, sellerId = 5L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"),
            quantity = quantity
        )
        ReflectionTestUtils.setField(item, "id", id)
        return item
    }

    private fun createRefund(id: Long = 1L): Refund {
        val refund = Refund.create(
            orderId = 10L, paymentId = 20L, buyerId = 1L, sellerId = 5L,
            reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            refundAmount = BigDecimal("25000"), isFullRefund = false
        )
        ReflectionTestUtils.setField(refund, "id", id)
        return refund
    }

    @Test
    fun 환불_요청을_생성한다_배송_준비중이면_자동승인() {
        val request = RefundCreateRequest(
            orderId = 10L, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
        )
        val order = createOrder()
        val orderItem = createOrderItem()
        val refund = createRefund()
        val refundItem = RefundItem.create(
            refundId = 1L, orderItemId = 1L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"), quantity = 1
        )
        ReflectionTestUtils.setField(refundItem, "id", 1L)
        val shipping = Shipping.create(
            orderId = 10L, buyerId = 1L, carrierName = "CJ",
            trackingNumber = "1234", recipientName = "홍길동",
            recipientPhone = "010-0000-0000", recipientAddress = "서울"
        )

        whenever(paymentQueryPort.findByOrderId(10L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(10L)).thenReturn(listOf(orderItem))
        whenever(refundItemRepository.findRefundedQuantitiesByOrderId(10L)).thenReturn(emptyList())
        whenever(refundRepository.save(any<Refund>())).thenReturn(refund)
        whenever(refundItemRepository.saveAll(any<List<RefundItem>>())).thenReturn(listOf(refundItem))
        whenever(shippingRepository.findByOrderId(10L)).thenReturn(shipping)

        val result = service.requestRefund(1L, request)

        assertThat(result).isNotNull
        verify(refundEventPublisher).publishRefundCompletedEvent(any())
    }

    @Test
    fun 결제정보가_없으면_예외가_발생한다() {
        val request = RefundCreateRequest(
            orderId = 10L, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
        )

        whenever(paymentQueryPort.findByOrderId(10L)).thenReturn(null)

        assertThatThrownBy { service.requestRefund(1L, request) }
            .isInstanceOf(RefundPaymentNotFoundException::class.java)
    }

    @Test
    fun 결제상태가_SUCCESS가_아니면_예외가_발생한다() {
        val request = RefundCreateRequest(
            orderId = 10L, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
        )
        val failedPayment = PaymentInfo(
            id = 20L, orderId = 10L, amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"), couponId = null, status = "FAILED"
        )

        whenever(paymentQueryPort.findByOrderId(10L)).thenReturn(failedPayment)

        assertThatThrownBy { service.requestRefund(1L, request) }
            .isInstanceOf(RefundException::class.java)
    }

    @Test
    fun 다른_구매자의_주문에_환불_요청하면_예외가_발생한다() {
        val request = RefundCreateRequest(
            orderId = 10L, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
        )
        val order = createOrder(buyerId = 99L)

        whenever(paymentQueryPort.findByOrderId(10L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))

        assertThatThrownBy { service.requestRefund(1L, request) }
            .isInstanceOf(RefundAccessDeniedException::class.java)
    }

    @Test
    fun 환불을_승인한다() {
        val refund = createRefund()
        val refundItem = RefundItem.create(
            refundId = 1L, orderItemId = 1L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"), quantity = 1
        )
        ReflectionTestUtils.setField(refundItem, "id", 1L)

        whenever(refundRepository.findById(1L)).thenReturn(Optional.of(refund))
        whenever(paymentQueryPort.findById(20L)).thenReturn(payment)
        whenever(refundItemRepository.findByRefundId(1L)).thenReturn(listOf(refundItem))
        whenever(refundRepository.save(any<Refund>())).thenReturn(refund)

        val result = service.approveRefund(1L, 5L)

        assertThat(result).isNotNull
        verify(refundEventPublisher).publishRefundCompletedEvent(any())
    }

    @Test
    fun 다른_판매자가_환불_승인하면_예외가_발생한다() {
        val refund = createRefund()

        whenever(refundRepository.findById(1L)).thenReturn(Optional.of(refund))
        whenever(paymentQueryPort.findById(20L)).thenReturn(payment)

        assertThatThrownBy { service.approveRefund(1L, 999L) }
            .isInstanceOf(RefundAccessDeniedException::class.java)
    }

    @Test
    fun 존재하지_않는_환불_승인_시_예외가_발생한다() {
        whenever(refundRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.approveRefund(999L, 5L) }
            .isInstanceOf(RefundNotFoundException::class.java)
    }

    @Test
    fun 환불을_거절한다() {
        val refund = createRefund()
        val refundItem = RefundItem.create(
            refundId = 1L, orderItemId = 1L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"), quantity = 1
        )
        ReflectionTestUtils.setField(refundItem, "id", 1L)
        val request = RefundApprovalRequest(rejectionReason = "사유 불충분")

        whenever(refundRepository.findById(1L)).thenReturn(Optional.of(refund))
        whenever(refundRepository.save(any<Refund>())).thenReturn(refund)
        whenever(refundItemRepository.findByRefundId(1L)).thenReturn(listOf(refundItem))

        val result = service.rejectRefund(1L, 5L, request)

        assertThat(result).isNotNull
    }

    @Test
    fun 다른_판매자가_환불_거절하면_예외가_발생한다() {
        val refund = createRefund()
        val request = RefundApprovalRequest(rejectionReason = "사유 불충분")

        whenever(refundRepository.findById(1L)).thenReturn(Optional.of(refund))

        assertThatThrownBy { service.rejectRefund(1L, 999L, request) }
            .isInstanceOf(RefundAccessDeniedException::class.java)
    }

    @Test
    fun 존재하지_않는_환불_거절_시_예외가_발생한다() {
        val request = RefundApprovalRequest(rejectionReason = "사유 불충분")

        whenever(refundRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.rejectRefund(999L, 5L, request) }
            .isInstanceOf(RefundNotFoundException::class.java)
    }
}
