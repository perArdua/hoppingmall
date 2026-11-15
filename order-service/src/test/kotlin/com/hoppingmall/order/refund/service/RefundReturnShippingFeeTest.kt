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
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.enum.RefundReason
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("단순 변심 환불의 반품 배송비")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundReturnShippingFeeTest {

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
    fun 배송_완료된_주문의_단순_변심_환불은_반품_배송비를_빼고_상품_금액과_배송비와_실제_환불액을_나눠_준다() {
        givenOrder(price = "25000", shipping = shipping(ShippingStatus.DELIVERED))

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.productAmount).isEqualByComparingTo("25000")
        assertThat(response.returnShippingFee).isEqualByComparingTo("3000")
        assertThat(response.refundAmount).isEqualByComparingTo("22000")
        assertThat(response.items.single().refundPrice).isEqualByComparingTo("25000")
    }

    @Test
    fun 배송_중인_주문의_단순_변심_환불도_반품_배송비를_뺀다() {
        givenOrder(price = "25000", shipping = shipping(ShippingStatus.IN_TRANSIT), delivered = false)

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.returnShippingFee).isEqualByComparingTo("3000")
        assertThat(response.refundAmount).isEqualByComparingTo("22000")
    }

    @Test
    fun 판매자_책임_사유는_반품_배송비_없이_상품_금액을_모두_돌려준다() {
        listOf(RefundReason.DEFECTIVE, RefundReason.WRONG_ITEM, RefundReason.NOT_AS_DESCRIBED).forEach { reason ->
            givenOrder(price = "25000", shipping = shipping(ShippingStatus.DELIVERED))

            val response = service.requestRefund(BUYER_ID, refundRequest(reason))

            assertThat(response.productAmount).isEqualByComparingTo("25000")
            assertThat(response.returnShippingFee).isEqualByComparingTo("0")
            assertThat(response.refundAmount).isEqualByComparingTo("25000")
        }
    }

    @Test
    fun 발송_전_단순_변심_환불은_돌려보낼_물건이_없어_반품_배송비가_없다() {
        givenOrder(price = "25000", shipping = null, delivered = false)

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.returnShippingFee).isEqualByComparingTo("0")
        assertThat(response.refundAmount).isEqualByComparingTo("25000")
        val captor = argumentCaptor<RefundCompletedEvent>()
        verify(refundEventPublisher).publishRefundCompletedEvent(captor.capture())
        assertThat(captor.firstValue.refundAmount).isEqualByComparingTo("25000")
    }

    @Test
    fun 반품_배송비가_환불할_상품_금액보다_크면_RFD015로_거절하고_저장하지_않는다() {
        givenOrder(price = "2999", shipping = shipping(ShippingStatus.DELIVERED))

        assertThatThrownBy { service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND)) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body?.code).isEqualTo("RFD015")
            }
        verify(refundRepository, never()).save(any<Refund>())
        verify(refundItemRepository, never()).saveAll(any<List<RefundItem>>())
    }

    @Test
    fun 반품_배송비가_상품_금액과_같으면_0원_환불로_받는다() {
        givenOrder(price = "3000", shipping = shipping(ShippingStatus.DELIVERED))

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.productAmount).isEqualByComparingTo("3000")
        assertThat(response.refundAmount).isEqualByComparingTo("0")
    }

    @Test
    fun 반품_배송비는_프로퍼티를_따른다() {
        ReflectionTestUtils.setField(service, "refundPolicy", RefundPolicyProperties(returnShippingFee = BigDecimal("5000")))
        givenOrder(price = "25000", shipping = shipping(ShippingStatus.DELIVERED))

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.returnShippingFee).isEqualByComparingTo("5000")
        assertThat(response.refundAmount).isEqualByComparingTo("20000")
    }

    @Test
    fun 쿠폰_비례로_나눈_상품_금액에서_반품_배송비를_뺀다() {
        givenOrder(price = "10000", quantity = 3, paid = "27000", shipping = shipping(ShippingStatus.DELIVERED))

        val response = service.requestRefund(BUYER_ID, refundRequest(RefundReason.CHANGE_OF_MIND))

        assertThat(response.productAmount).isEqualByComparingTo("9000")
        assertThat(response.returnShippingFee).isEqualByComparingTo("3000")
        assertThat(response.refundAmount).isEqualByComparingTo("6000")
        assertThat(response.items.single().refundPrice).isEqualByComparingTo("9000")
    }

    private fun givenOrder(
        price: String,
        shipping: Shipping?,
        quantity: Int = 1,
        paid: String? = null,
        delivered: Boolean = true
    ) {
        val total = BigDecimal(price).multiply(BigDecimal(quantity))
        val order = Order.create(buyerId = BUYER_ID, totalAmount = total)
        val path = when {
            delivered -> listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            shipping != null -> listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED)
            else -> listOf(OrderStatus.PAYING, OrderStatus.PAID)
        }
        path.forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        Mockito.lenient().`when`(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        Mockito.lenient().`when`(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = 5L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal(price), quantity = quantity
        )
        ReflectionTestUtils.setField(item, "id", ORDER_ITEM_ID)
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(item))
        Mockito.lenient().`when`(paymentQueryPort.findByOrderId(ORDER_ID)).thenReturn(
            PaymentInfo(
                id = 20L, orderId = ORDER_ID, amount = paid?.let { BigDecimal(it) } ?: total,
                pointAmount = BigDecimal.ZERO, couponId = paid?.let { 1L }, status = "SUCCESS"
            )
        )
    }

    private fun shipping(status: ShippingStatus): Shipping {
        val shipping = Shipping.create(
            orderId = ORDER_ID, buyerId = BUYER_ID, carrierName = "택배", trackingNumber = "T-1",
            recipientName = "구매자", recipientPhone = "010-0000-0000", recipientAddress = "주소"
        )
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        if (status == ShippingStatus.DELIVERED) {
            shipping.updateStatus(ShippingStatus.DELIVERED)
        }
        return shipping
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
