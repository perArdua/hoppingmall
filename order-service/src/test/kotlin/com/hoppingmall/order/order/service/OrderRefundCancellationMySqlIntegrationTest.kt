package com.hoppingmall.order.order.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.internal.InternalOrderController
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

@DisplayName("환불된 주문의 취소와 끝 상태 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(OrderCommandServiceImpl::class, InternalOrderController::class)
class OrderRefundCancellationMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var internalOrderController: InternalOrderController

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var refundRepository: RefundRepository

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @MockitoBean
    private lateinit var orderMetrics: OrderMetrics

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM refund_items")
        jdbcTemplate.update("DELETE FROM refunds")
        mockMvc = MockMvcBuilders.standaloneSetup(internalOrderController)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun 전체_환불의_내부_취소_입구는_PAID_주문을_REFUNDED로_보낸다() {
        val orderId = saveOrder("PAID")

        internalCancel(orderId).andExpect { status { isOk() } }

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED")
    }

    @Test
    fun 전체_환불의_내부_취소_입구는_SHIPPED_주문을_REFUNDED로_보낸다() {
        val orderId = saveOrder("SHIPPED")

        internalCancel(orderId).andExpect { status { isOk() } }

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED")
    }

    @Test
    fun 전체_환불의_내부_취소_입구는_DELIVERED_주문을_REFUNDED로_보낸다() {
        val orderId = saveOrder("DELIVERED")

        internalCancel(orderId).andExpect { status { isOk() } }

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED")
    }

    @Test
    fun 내부_취소_입구를_두_번_불러도_200이고_REFUNDED_그대로다() {
        val orderId = saveOrder("DELIVERED")

        internalCancel(orderId).andExpect { status { isOk() } }
        internalCancel(orderId).andExpect { status { isOk() } }

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED")
    }

    @Test
    fun 내부_취소_입구는_PAYING과_CANCEL_REQUESTED_주문을_지금처럼_CANCELLED로_보낸다() {
        val paying = saveOrder("PAYING")
        val cancelRequested = saveOrder("CANCEL_REQUESTED")

        internalCancel(paying).andExpect { status { isOk() } }
        internalCancel(cancelRequested).andExpect { status { isOk() } }

        assertThat(orderStatus(paying)).isEqualTo("CANCELLED")
        assertThat(orderStatus(cancelRequested)).isEqualTo("CANCELLED")
    }

    @Test
    fun 완료된_부분_환불이_있는_PAID_주문을_사용자가_취소하면_400이고_재고와_결제를_부르지_않는다() {
        val orderId = saveOrder("PAID")
        saveRefund(orderId, complete = true)

        assertThatThrownBy { orderCommandService.cancelOrder(BUYER_ID, orderId) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({
                val errorCode = (it as BusinessException).errorCode
                assertThat(errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(errorCode.code).isEqualTo("ORD012")
            })

        assertThat(orderStatus(orderId)).isEqualTo("PAID")
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        verifyNoInteractions(inventoryCommandPort)
        verifyNoInteractions(paymentCommandPort)
    }

    @Test
    fun 거절된_환불만_있는_PAID_주문은_지금처럼_취소를_요청한다() {
        val orderId = saveOrder("PAID")
        saveRefund(orderId, complete = false, reject = true)

        val response = orderCommandService.cancelOrder(BUYER_ID, orderId)

        assertThat(response.status.name).isEqualTo("CANCEL_REQUESTED")
        assertThat(orderStatus(orderId)).isEqualTo("CANCEL_REQUESTED")
    }

    @Test
    fun 관리자도_공개_상태_변경으로_PAID_주문을_REFUNDED로_보낼_수_없다() {
        val orderId = saveOrder("PAID")

        assertThatThrownBy {
            orderCommandService.updateOrderStatus(
                orderId, OrderStatusUpdateRequest(OrderStatus.valueOf("REFUNDED")), ADMIN_ID, true
            )
        }.isInstanceOf(OrderInvalidStatusException::class.java)

        assertThat(orderStatus(orderId)).isEqualTo("PAID")
    }

    @Test
    fun REFUNDED_주문은_사용자가_취소할_수_없다() {
        val orderId = saveOrder("PAID")
        internalCancel(orderId).andExpect { status { isOk() } }

        assertThatThrownBy { orderCommandService.cancelOrder(BUYER_ID, orderId) }
            .isInstanceOf(OrderInvalidStatusException::class.java)

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED")
        verifyNoInteractions(inventoryCommandPort)
        verifyNoInteractions(paymentCommandPort)
    }

    private fun internalCancel(orderId: Long) =
        mockMvc.post("/internal/api/v1/orders/$orderId/cancel")

    private fun saveOrder(status: String): Long {
        val orderId = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("30000"))).id!!
        orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = 100L,
                productName = "상품", productPrice = BigDecimal("10000"),
                quantity = 3, reservationId = "rsv-$orderId"
            )
        )
        jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status, orderId)
        return orderId
    }

    private fun saveRefund(orderId: Long, complete: Boolean, reject: Boolean = false) {
        val refund = Refund.create(
            orderId = orderId, paymentId = 900L, buyerId = BUYER_ID, sellerId = SELLER_ID,
            reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            refundAmount = BigDecimal("10000"), isFullRefund = false
        )
        if (complete) {
            refund.approve(BUYER_ID)
            refund.complete()
        }
        if (reject) {
            refund.reject("거절", SELLER_ID)
        }
        refundRepository.saveAndFlush(refund)
    }

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val ADMIN_ID = 99L
    }
}
