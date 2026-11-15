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
import com.hoppingmall.order.refund.enum.RefundStatus
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
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("청약철회 제한 항목의 단순 변심 환불")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundWithdrawalRestrictionTest {

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
            (invocation.arguments[0] as TransactionCallback<Any?>).doInTransaction(mock())
        }
        Mockito.lenient().`when`(paymentQueryPort.findByOrderId(ORDER_ID)).thenReturn(
            PaymentInfo(
                id = 20L, orderId = ORDER_ID, amount = BigDecimal("40000"),
                pointAmount = BigDecimal.ZERO, couponId = null, status = "SUCCESS"
            )
        )
        Mockito.lenient().`when`(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(deliveredOrder()))
        Mockito.lenient().`when`(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(null)
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(orderItem(RESTRICTED_ITEM_ID, restricted = true), orderItem(FREE_ITEM_ID, restricted = false))
        )
        Mockito.lenient().`when`(refundItemRepository.findRefundQuantitiesByStatus(ORDER_ID)).thenReturn(emptyList())
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
    fun 제한된_항목의_단순_변심_환불은_400_RFD016으로_거절하고_저장하지_않는다() {
        assertRejected(request(RefundReason.CHANGE_OF_MIND, RESTRICTED_ITEM_ID))
    }

    @Test
    fun 제한된_항목이_하나라도_섞인_단순_변심_환불은_전체를_거절한다() {
        assertRejected(request(RefundReason.CHANGE_OF_MIND, FREE_ITEM_ID, RESTRICTED_ITEM_ID))
    }

    @Test
    fun 제한된_항목도_판매자_잘못_사유의_환불은_받는다() {
        listOf(RefundReason.DEFECTIVE, RefundReason.WRONG_ITEM, RefundReason.NOT_AS_DESCRIBED).forEach { reason ->
            val response = service.requestRefund(BUYER_ID, request(reason, RESTRICTED_ITEM_ID))

            assertThat(response.status).isEqualTo(RefundStatus.REQUESTED)
        }
    }

    @Test
    fun 제한되지_않은_항목의_단순_변심_환불은_그대로_받는다() {
        val response = service.requestRefund(BUYER_ID, request(RefundReason.CHANGE_OF_MIND, FREE_ITEM_ID))

        assertThat(response.status).isIn(RefundStatus.REQUESTED, RefundStatus.COMPLETED)
        verify(refundItemRepository).saveAll(any<List<RefundItem>>())
    }

    private fun assertRejected(request: RefundCreateRequest) {
        assertThatThrownBy { service.requestRefund(BUYER_ID, request) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body?.code).isEqualTo("RFD016")
            }
        verify(refundRepository, never()).save(any<Refund>())
        verify(refundItemRepository, never()).saveAll(any<List<RefundItem>>())
    }

    private fun deliveredOrder(): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("40000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED).forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "deliveredAt", LocalDateTime.now().minusDays(2))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
    }

    private fun orderItem(id: Long, restricted: Boolean): OrderItem {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = 5L, productId = 100L + id,
            productName = "상품$id", productPrice = BigDecimal("20000"), quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", id)
        if (restricted) {
            ReflectionTestUtils.setField(item, "withdrawalRestricted", true)
            ReflectionTestUtils.setField(item, "withdrawalRestrictionReason", "CUSTOM_MADE")
        }
        return item
    }

    private fun request(reason: RefundReason, vararg itemIds: Long) = RefundCreateRequest(
        orderId = ORDER_ID, reason = reason, reasonDetail = null,
        items = itemIds.map { RefundItemRequest(orderItemId = it, quantity = 1) }
    )

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val RESTRICTED_ITEM_ID = 1L
        private const val FREE_ITEM_ID = 2L
        private const val REFUND_ID = 30L
    }
}
