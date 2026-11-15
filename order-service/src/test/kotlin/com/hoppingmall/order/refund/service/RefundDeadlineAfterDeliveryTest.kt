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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("배송 완료 뒤 환불 요청 기한")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundDeadlineAfterDeliveryTest {

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
                id = 20L, orderId = ORDER_ID, amount = BigDecimal("25000"),
                pointAmount = BigDecimal.ZERO, couponId = null, status = "SUCCESS"
            )
        )
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(orderItem()))
        Mockito.lenient().`when`(refundItemRepository.findRefundedQuantitiesByOrderId(ORDER_ID)).thenReturn(emptyList())
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
    fun 배송_완료_뒤_30일이_지나면_환불_요청을_거절하고_저장하지_않는다() {
        givenOrder(deliveredOrder(deliveredDaysAgo = 31))

        assertThatThrownBy { service.requestRefund(BUYER_ID, refundRequest()) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body?.code).isEqualTo("RFD011")
            }

        verify(refundRepository, never()).save(any<Refund>())
        verify(refundItemRepository, never()).saveAll(any<List<RefundItem>>())
        verify(refundEventPublisher, never()).publishRefundCompletedEvent(any())
    }

    @Test
    fun 배송_완료_뒤_단순_변심_기한_안이면_환불_요청을_받는다() {
        givenOrder(deliveredOrder(deliveredDaysAgo = 6))

        val response = service.requestRefund(BUYER_ID, refundRequest())

        assertThat(response.id).isEqualTo(REFUND_ID)
        verify(refundRepository).save(any<Refund>())
    }

    @Test
    fun 배송_완료_시각이_없는_기존_주문에는_기한을_적용하지_않는다() {
        val order = deliveredOrder(deliveredDaysAgo = 365)
        ReflectionTestUtils.setField(order, "deliveredAt", null)
        givenOrder(order)

        val response = service.requestRefund(BUYER_ID, refundRequest())

        assertThat(response.id).isEqualTo(REFUND_ID)
    }

    @Test
    fun 기한_일수는_프로퍼티로_바꾼다() {
        ReflectionTestUtils.setField(service, "refundPolicy", RefundPolicyProperties(changeOfMindWindowDays = 3))
        givenOrder(deliveredOrder(deliveredDaysAgo = 4))

        assertThatThrownBy { service.requestRefund(BUYER_ID, refundRequest()) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(handler.handleBusinessException(it).body?.code).isEqualTo("RFD011")
            }

        verify(refundRepository, never()).save(any<Refund>())
    }

    private fun givenOrder(order: Order) {
        Mockito.lenient().`when`(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
    }

    private fun deliveredOrder(deliveredDaysAgo: Long): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("25000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            .forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "deliveredAt", LocalDateTime.now().minusDays(deliveredDaysAgo))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
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

    private fun orderItem(): OrderItem {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = 5L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"),
            quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", ORDER_ITEM_ID)
        return item
    }

    private fun refundRequest() = RefundCreateRequest(
        orderId = ORDER_ID, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
        items = listOf(RefundItemRequest(orderItemId = ORDER_ITEM_ID, quantity = 1))
    )

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val ORDER_ITEM_ID = 1L
        private const val REFUND_ID = 30L
    }
}
