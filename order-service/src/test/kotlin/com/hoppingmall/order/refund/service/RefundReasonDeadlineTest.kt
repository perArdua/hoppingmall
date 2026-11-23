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
import com.hoppingmall.order.refund.config.RefundPolicyProperties
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.RefundReasonConverter
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("환불 사유별 요청 기한과 판매자 확인")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundReasonDeadlineTest {

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

    private val sellerFaultReasons = listOf(RefundReason.DEFECTIVE, RefundReason.WRONG_ITEM, RefundReason.NOT_AS_DESCRIBED)

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        Mockito.lenient().`when`(paymentQueryPort.findByOrderId(ORDER_ID)).thenReturn(
            PaymentInfo(
                id = 20L, orderId = ORDER_ID, amount = BigDecimal("25000"),
                pointAmount = BigDecimal.ZERO, couponId = null, status = "SUCCESS"
            )
        )
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(orderItem()))
        Mockito.lenient().`when`(refundItemRepository.findRefundedQuantitiesByOrderId(ORDER_ID)).thenReturn(emptyList())
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
    fun 단순_변심은_받은_날부터_7일째_날까지_받는다() {
        givenDelivered(receivedAt = LocalDate.now().minusDays(7).atTime(0, 1))

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.status).isEqualTo(RefundStatus.REQUESTED)
        verify(refundRepository).save(any<Refund>())
    }

    @Test
    fun 단순_변심은_받은_날부터_8일째에_RFD011로_거절하고_저장하지_않는다() {
        givenDelivered(receivedAt = LocalDate.now().minusDays(8).atTime(23, 59))

        assertRejected(RefundReason.CHANGE_OF_MIND, "RFD011")
    }

    @Test
    fun 하자류_사유는_받은_날부터_31일째에도_받고_판매자_확인을_기다린다() {
        sellerFaultReasons.forEach { reason ->
            givenDelivered(receivedAt = LocalDateTime.now().minusDays(31))

            val response = service.requestRefund(BUYER_ID, refundRequest(reason))

            assertThat(response.status).isEqualTo(RefundStatus.REQUESTED)
            assertThat(response.reason).isEqualTo(reason)
        }
        verify(refundEventPublisher, never()).publishRefundCompletedEvent(any())
    }

    @Test
    fun 하자류_사유는_받은_날부터_3개월_안이면_받는다() {
        sellerFaultReasons.forEach { reason ->
            givenDelivered(receivedAt = LocalDate.now().minusMonths(3).plusDays(1).atTime(0, 1))

            val response = service.requestRefund(BUYER_ID, refundRequest(reason))

            assertThat(response.status).isEqualTo(RefundStatus.REQUESTED)
        }
    }

    @Test
    fun 하자류_사유는_받은_날부터_3개월이_지나면_RFD012로_거절하고_저장하지_않는다() {
        sellerFaultReasons.forEach { reason ->
            givenDelivered(receivedAt = LocalDate.now().minusMonths(3).minusDays(1).atTime(23, 59))

            assertRejected(reason, "RFD012")
        }
    }

    @Test
    fun 기간은_프로퍼티를_따른다() {
        ReflectionTestUtils.setField(
            service, "refundPolicy", RefundPolicyProperties(changeOfMindWindowDays = 3, sellerFaultWindowMonths = 1)
        )

        givenDelivered(receivedAt = LocalDateTime.now().minusDays(4))
        assertRejected(RefundReason.CHANGE_OF_MIND, "RFD011")

        givenDelivered(receivedAt = LocalDate.now().minusMonths(1).minusDays(1).atStartOfDay())
        assertRejected(RefundReason.DEFECTIVE, "RFD012")
    }

    @Test
    fun 주문의_배송_완료_시각이_비면_배송_행의_완료_시각으로_판단한다() {
        val order = deliveredOrder(receivedAt = null)
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now())
        givenOrder(order)
        givenShipping(deliveredShipping(completedAt = LocalDateTime.now().minusDays(10)))

        assertRejected(RefundReason.CHANGE_OF_MIND, "RFD011")
    }

    @Test
    fun 배송_완료_행도_없으면_배송_완료_주문의_마지막_변경_시각으로_판단한다() {
        val order = deliveredOrder(receivedAt = null)
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now().minusDays(10))
        givenOrder(order)
        givenShipping(null)

        assertRejected(RefundReason.CHANGE_OF_MIND, "RFD011")
    }

    @Test
    fun 하자류_사유는_발송_전이라도_바로_완료하지_않고_판매자_확인을_기다린다() {
        givenOrder(paidOrder())
        givenShipping(null)

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.DEFECTIVE))

        assertThat(response.status).isEqualTo(RefundStatus.REQUESTED)
        verify(refundEventPublisher, never()).publishRefundCompletedEvent(any())
    }

    @Test
    fun 단순_변심의_발송_전_환불은_지금처럼_바로_완료된다() {
        givenOrder(paidOrder())
        givenShipping(null)

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.status).isEqualTo(RefundStatus.COMPLETED)
        verify(refundEventPublisher).publishRefundCompletedEvent(any())
    }

    @Test
    fun 옛_사유_값으로는_새_환불을_요청할_수_없다() {
        listOf(
            RefundReason.DEFECTIVE_PRODUCT, RefundReason.WRONG_PRODUCT, RefundReason.LATE_DELIVERY, RefundReason.OTHER
        ).forEach { reason ->
            assertThatThrownBy { service.requestRefund(BUYER_ID, refundRequest(reason)) }
                .isInstanceOfSatisfying(BusinessException::class.java) {
                    val response = handler.handleBusinessException(it)
                    assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                    assertThat(response.body?.code).isEqualTo("RFD013")
                }
        }
        verifyNoInteractions(paymentQueryPort)
        verify(refundRepository, never()).save(any<Refund>())
    }

    @Test
    fun 옛_사유_값이_저장된_환불은_그대로_읽는다() {
        val converter = RefundReasonConverter()

        assertThat(converter.convertToEntityAttribute("DEFECTIVE_PRODUCT")).isEqualTo(RefundReason.DEFECTIVE_PRODUCT)
        assertThat(converter.convertToEntityAttribute("LATE_DELIVERY")).isEqualTo(RefundReason.LATE_DELIVERY)
        assertThat(converter.convertToDatabaseColumn(RefundReason.NOT_AS_DESCRIBED)).isEqualTo("NOT_AS_DESCRIBED")
    }

    private fun assertRejected(reason: RefundReason, code: String) {
        assertThatThrownBy { service.requestRefund(BUYER_ID, refundRequest(reason)) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body?.code).isEqualTo(code)
            }
        verify(refundRepository, never()).save(any<Refund>())
        verify(refundItemRepository, never()).saveAll(any<List<RefundItem>>())
        verify(refundEventPublisher, never()).publishRefundCompletedEvent(any())
    }

    private fun givenDelivered(receivedAt: LocalDateTime) {
        givenOrder(deliveredOrder(receivedAt))
        givenShipping(deliveredShipping(completedAt = receivedAt))
    }

    private fun givenOrder(order: Order) {
        Mockito.lenient().`when`(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
    }

    private fun givenShipping(shipping: Shipping?) {
        Mockito.lenient().`when`(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)
    }

    private fun paidOrder(): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("25000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID).forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
    }

    private fun deliveredOrder(receivedAt: LocalDateTime?): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("25000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            .forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "deliveredAt", receivedAt)
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
    }

    private fun deliveredShipping(completedAt: LocalDateTime): Shipping {
        val shipping = Shipping.create(
            orderId = ORDER_ID, buyerId = BUYER_ID, carrierName = "택배", trackingNumber = "T-1",
            recipientName = "구매자", recipientPhone = "010-0000-0000", recipientAddress = "주소"
        )
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(ShippingStatus.DELIVERED)
        ReflectionTestUtils.setField(shipping, "updatedAt", completedAt)
        return shipping
    }

    private fun orderItem(): OrderItem {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = 5L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"),
            quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", ORDER_ITEM_ID)
        return item
    }

    private fun refundRequest(reason: RefundReason) = RefundCreateRequest(
        orderId = ORDER_ID, reason = reason, reasonDetail = null,
        items = listOf(RefundItemRequest(orderItemId = ORDER_ITEM_ID, quantity = 1))
    )

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val ORDER_ITEM_ID = 1L
        private const val REFUND_ID = 30L
    }
}
