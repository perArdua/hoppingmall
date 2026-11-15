package com.hoppingmall.order.order.controller

import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.config.SecurityConfig
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.response.SellerOrderResponse
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.service.OrderCommandServiceImpl
import com.hoppingmall.order.order.service.OrderQueryService
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.service.ShippingCommandServiceImpl
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("판매자 주문 목록·상세 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(controllers = [OrderController::class])
@Import(SecurityConfig::class, OrderCommandServiceImpl::class, ShippingCommandServiceImpl::class)
class SellerOrderApiTest {

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

    @MockitoBean
    private lateinit var shippingRepository: ShippingRepository

    @Test
    fun 판매자는_자기_주문_목록을_상태와_쪽으로_받는다() {
        whenever(orderQueryService.getSellerOrders(eq(SELLER_ID), eq(OrderStatus.PAID), any()))
            .thenReturn(SliceImpl(listOf(response()), PageRequest.of(1, 5), true))

        mockMvc.get("/api/v1/orders/seller?status=PAID&page=1&size=5") {
            header("X-User-Id", SELLER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].id") { value(ORDER_ID) }
            jsonPath("$.data.content[0].seller_total_amount") { value(3000) }
            jsonPath("$.data.content[0].items[0].seller_id") { value(SELLER_ID) }
            jsonPath("$.data.content[0].buyer_id") { doesNotExist() }
            jsonPath("$.data.last") { value(false) }
        }

        val pageable = argumentCaptor<Pageable>()
        verify(orderQueryService).getSellerOrders(eq(SELLER_ID), eq(OrderStatus.PAID), pageable.capture())
        org.assertj.core.api.Assertions.assertThat(pageable.firstValue.pageNumber).isEqualTo(1)
        org.assertj.core.api.Assertions.assertThat(pageable.firstValue.pageSize).isEqualTo(5)
    }

    @Test
    fun 상태를_주지_않으면_전체를_묻는다() {
        whenever(orderQueryService.getSellerOrders(eq(SELLER_ID), anyOrNull(), any()))
            .thenReturn(SliceImpl(emptyList(), PageRequest.of(0, 20), false))

        mockMvc.get("/api/v1/orders/seller") {
            header("X-User-Id", SELLER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isOk() } }

        verify(orderQueryService).getSellerOrders(eq(SELLER_ID), eq(null), any())
    }

    @Test
    fun 판매자는_자기_주문_상세를_받는다() {
        whenever(orderQueryService.getSellerOrder(ORDER_ID, SELLER_ID)).thenReturn(response())

        mockMvc.get("/api/v1/orders/seller/$ORDER_ID") {
            header("X-User-Id", SELLER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(ORDER_ID) }
            jsonPath("$.data.status") { value("PAID") }
        }
    }

    @Test
    fun 구매자와_관리자는_판매자_주문_입구를_쓸_수_없다() {
        listOf("BUYER", "ADMIN").forEach { role ->
            mockMvc.get("/api/v1/orders/seller") {
                header("X-User-Id", "1")
                header("x-user-role", role)
            }.andExpect { status { isForbidden() } }
            mockMvc.get("/api/v1/orders/seller/$ORDER_ID") {
                header("X-User-Id", "1")
                header("x-user-role", role)
            }.andExpect { status { isForbidden() } }
        }

        verify(orderQueryService, never()).getSellerOrders(any(), anyOrNull(), any())
        verify(orderQueryService, never()).getSellerOrder(any(), any())
    }

    @Test
    fun 없는_상태_값은_400이다() {
        mockMvc.get("/api/v1/orders/seller?status=UNKNOWN") {
            header("X-User-Id", SELLER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isBadRequest() } }
    }

    private fun response() = SellerOrderResponse(
        id = ORDER_ID,
        status = OrderStatus.PAID,
        sellerTotalAmount = BigDecimal("3000"),
        items = listOf(
            com.hoppingmall.order.order.dto.response.OrderItemResponse(
                id = 5L, sellerId = SELLER_ID, productId = 100L, productName = "상품",
                productPrice = BigDecimal("3000"), quantity = 1, totalPrice = BigDecimal("3000")
            )
        ),
        createdAt = LocalDateTime.of(2026, 11, 16, 10, 0),
        shippingAddress = null
    )

    companion object {
        private const val ORDER_ID = 1L
        private const val SELLER_ID = 20L
    }
}
