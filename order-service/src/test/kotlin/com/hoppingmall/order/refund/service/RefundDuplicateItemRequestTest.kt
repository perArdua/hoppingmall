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
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
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
import java.util.Optional

@DisplayName("같은 주문 항목이 두 번 든 환불 요청")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundDuplicateItemRequestTest {

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
        Mockito.lenient().`when`(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(paidOrder()))
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(orderItem(quantity = 1)))
        Mockito.lenient().`when`(refundItemRepository.findRefundedQuantitiesByOrderId(ORDER_ID)).thenReturn(emptyList())
        service = RefundCommandServiceImpl(
            refundRepository, refundItemRepository, orderRepository,
            orderItemRepository, paymentQueryPort, shippingRepository,
            refundEventPublisher, transactionTemplate
        )
    }

    @Test
    fun 수량_1인_항목을_1개씩_두_번_요청하면_400으로_거절하고_환불을_저장하지_않는다() {
        val request = refundRequest(
            RefundItemRequest(orderItemId = ORDER_ITEM_ID, quantity = 1),
            RefundItemRequest(orderItemId = ORDER_ITEM_ID, quantity = 1)
        )

        assertThatThrownBy { service.requestRefund(BUYER_ID, request) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body?.code).isEqualTo("RFD010")
            }

        verify(refundRepository, never()).save(any<Refund>())
        verify(refundItemRepository, never()).saveAll(any<List<RefundItem>>())
        verify(refundEventPublisher, never()).publishRefundCompletedEvent(any())
    }

    @Test
    fun 합계가_주문_수량_안이어도_같은_항목이_두_번_들면_거절한다() {
        stubOrderItemQuantity(2)
        val request = refundRequest(
            RefundItemRequest(orderItemId = ORDER_ITEM_ID, quantity = 1),
            RefundItemRequest(orderItemId = ORDER_ITEM_ID, quantity = 1)
        )

        assertThatThrownBy { service.requestRefund(BUYER_ID, request) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(handler.handleBusinessException(it).body?.code).isEqualTo("RFD010")
            }

        verify(refundRepository, never()).save(any<Refund>())
    }

    private fun stubOrderItemQuantity(quantity: Int) {
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(orderItem(quantity)))
    }

    private fun refundRequest(vararg items: RefundItemRequest) = RefundCreateRequest(
        orderId = ORDER_ID, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null, items = items.toList()
    )

    private fun paidOrder(): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("25000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
    }

    private fun orderItem(quantity: Int): OrderItem {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = 5L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"),
            quantity = quantity
        )
        ReflectionTestUtils.setField(item, "id", ORDER_ITEM_ID)
        return item
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val ORDER_ITEM_ID = 1L
    }
}
