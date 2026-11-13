package com.hoppingmall.order.order.controller

import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.config.SecurityConfig
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.service.OrderCommandServiceImpl
import com.hoppingmall.order.order.service.OrderQueryService
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.micrometer.tracing.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("공개 주문 상태 변경 API로 REFUNDED 요청")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(controllers = [OrderController::class])
@Import(SecurityConfig::class, OrderCommandServiceImpl::class)
class OrderRefundedStatusRequestTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var orderQueryService: OrderQueryService

    @MockitoBean
    private lateinit var orderRepository: OrderRepository

    @MockitoBean
    private lateinit var orderItemRepository: OrderItemRepository

    @MockitoBean
    private lateinit var cartItemRepository: CartItemRepository

    @MockitoBean
    private lateinit var refundRepository: RefundRepository

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @MockitoBean
    private lateinit var orderMetrics: OrderMetrics

    @MockitoBean
    private lateinit var transactionTemplate: TransactionTemplate

    @MockitoBean
    private lateinit var tracer: Tracer

    @Test
    fun 구매자가_자기_PAID_주문을_REFUNDED로_바꾸려_하면_403이고_주문은_PAID에_남는다() {
        val order = paidOrder()

        requestRefunded(BUYER_ID, "BUYER")
            .andExpect { status { isForbidden() } }

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun 판매자가_PAID_주문을_REFUNDED로_바꾸려_하면_400이고_주문은_PAID에_남는다() {
        val order = paidOrder()

        requestRefunded(SELLER_ID, "SELLER")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("ORD003") }
            }

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun 관리자가_PAID_주문을_REFUNDED로_바꾸려_하면_400이고_주문은_PAID에_남는다() {
        val order = paidOrder()

        requestRefunded(ADMIN_ID, "ADMIN")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("ORD003") }
            }

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    private fun requestRefunded(userId: Long, role: String): ResultActionsDsl =
        mockMvc.patch("/api/v1/orders/$ORDER_ID/status") {
            header("X-User-Id", userId.toString())
            header("x-user-role", role)
            header("Idempotency-Key", "refunded-$userId")
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"REFUNDED"}"""
        }

    private fun paidOrder(): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        return order
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 20L
        private const val ADMIN_ID = 99L
    }
}
