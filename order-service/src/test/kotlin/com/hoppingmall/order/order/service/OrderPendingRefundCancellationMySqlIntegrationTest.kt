package com.hoppingmall.order.order.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
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
import java.math.BigDecimal

@DisplayName("승인 대기 환불이 있는 주문의 사용자 취소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(OrderCommandServiceImpl::class)
class OrderPendingRefundCancellationMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

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

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM refund_items")
        jdbcTemplate.update("DELETE FROM refunds")
    }

    @Test
    fun 승인_대기_중인_환불이_있는_PAID_주문을_사용자가_취소하면_400이고_재고와_결제를_부르지_않는다() {
        val orderId = savePaidOrder()
        savePendingRefund(orderId)

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

    private fun savePaidOrder(): Long {
        val orderId = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("30000"))).id!!
        orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = 100L,
                productName = "상품", productPrice = BigDecimal("10000"),
                quantity = 3, reservationId = "rsv-$orderId"
            )
        )
        jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId)
        return orderId
    }

    private fun savePendingRefund(orderId: Long) {
        refundRepository.saveAndFlush(
            Refund.create(
                orderId = orderId, paymentId = 900L, buyerId = BUYER_ID, sellerId = SELLER_ID,
                reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
                refundAmount = BigDecimal("10000"), isFullRefund = false
            )
        )
    }

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
    }
}
