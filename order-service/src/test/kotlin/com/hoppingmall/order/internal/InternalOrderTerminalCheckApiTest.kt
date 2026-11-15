package com.hoppingmall.order.internal

import com.hoppingmall.order.config.SecurityConfig
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import io.micrometer.tracing.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("주문 끝남 확인 내부 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(controllers = [InternalOrderController::class], properties = ["internal.service.token=$TERMINAL_TOKEN"])
@Import(SecurityConfig::class)
class InternalOrderTerminalCheckApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var orderRepository: OrderRepository

    @MockitoBean
    private lateinit var orderItemRepository: OrderItemRepository

    @MockitoBean
    private lateinit var shippingRepository: ShippingRepository

    @MockitoBean
    private lateinit var refundItemRepository: RefundItemRepository

    @MockitoBean
    private lateinit var refundRepository: RefundRepository

    @MockitoBean
    private lateinit var tracer: Tracer

    @Test
    fun 예약_id_목록을_받아_끝난_주문의_예약_id만_돌려준다() {
        givenOrders(
            order(1L, OrderStatus.CANCELLED, daysAgo = 31),
            order(2L, OrderStatus.PAID, daysAgo = 365),
            order(3L, OrderStatus.REFUNDED, daysAgo = 40)
        )
        givenItems(item(11L, 3L, "rsv-c"), item(12L, 1L, "rsv-a"), item(13L, 2L, "rsv-b"))

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":["rsv-c","rsv-b","rsv-a"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("SUCCESS") }
            jsonPath("$.data.terminal_reservation_ids.length()") { value(2) }
            jsonPath("$.data.terminal_reservation_ids[0]") { value("rsv-a") }
            jsonPath("$.data.terminal_reservation_ids[1]") { value("rsv-c") }
        }
    }

    @Test
    fun 모르는_예약_id는_끝난_것으로_답하지_않는다() {
        givenOrders(order(1L, OrderStatus.CANCELLED, daysAgo = 31))
        givenItems(item(11L, 1L, "rsv-known"))

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":["rsv-known","rsv-unknown"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.terminal_reservation_ids.length()") { value(1) }
            jsonPath("$.data.terminal_reservation_ids[0]") { value("rsv-known") }
        }
    }

    @Test
    fun 주문_행이_없는_주문_항목의_예약_id는_끝난_것으로_답하지_않는다() {
        givenOrders()
        givenItems(item(11L, 99L, "rsv-orphan"))

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":["rsv-orphan"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.terminal_reservation_ids.length()") { value(0) }
        }
    }

    @Test
    fun 같은_예약_id를_가진_주문이_하나라도_끝나지_않았으면_끝난_것으로_답하지_않는다() {
        givenOrders(
            order(1L, OrderStatus.CANCELLED, daysAgo = 31),
            order(2L, OrderStatus.DELIVERED, daysAgo = 365)
        )
        givenItems(item(11L, 1L, "rsv-shared"), item(12L, 2L, "rsv-shared"))

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":["rsv-shared"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.terminal_reservation_ids.length()") { value(0) }
        }
    }

    @Test
    fun 끝난_주문은_취소와_환불이_더는_없는_상태이면서_그_상태가_된_지_30일이_지난_주문이다() {
        givenOrders(
            order(1L, OrderStatus.CANCELLED, daysAgo = 29),
            order(2L, OrderStatus.CANCELLED, daysAgo = 31)
        )
        givenItems(item(11L, 1L, "rsv-recent"), item(12L, 2L, "rsv-old"))

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":["rsv-recent","rsv-old","rsv-old"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.terminal_reservation_ids.length()") { value(1) }
            jsonPath("$.data.terminal_reservation_ids[0]") { value("rsv-old") }
        }

        val asked = argumentCaptor<Collection<String>>()
        verify(orderItemRepository).findByReservationIdIn(asked.capture())
        assertThat(asked.firstValue).containsExactly("rsv-recent", "rsv-old")
    }

    @Test
    fun 빈_목록이면_조회하지_않고_빈_목록을_돌려준다() {
        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":[]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.terminal_reservation_ids.length()") { value(0) }
        }

        verify(orderItemRepository, never()).findByReservationIdIn(any())
    }

    @Test
    fun 예약_id가_500개를_넘으면_400이고_조회하지_않는다() {
        val ids = (1..501).joinToString(",") { "\"rsv-$it\"" }

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":[$ids]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }

        verify(orderItemRepository, never()).findByReservationIdIn(any())
    }

    @Test
    fun 예약_id가_500개면_받는다() {
        givenOrders()
        givenItems()
        val ids = (1..500).joinToString(",") { "\"rsv-$it\"" }

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":[$ids]}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun reservation_ids가_없으면_400이고_조회하지_않는다() {
        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"order_ids":[1]}"""
        }.andExpect { status { isBadRequest() } }

        verify(orderItemRepository, never()).findByReservationIdIn(any())
    }

    @Test
    fun 내부_토큰이_없으면_401이고_주문을_읽지_않는다() {
        mockMvc.post(PATH) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reservation_ids":["rsv-a"]}"""
        }.andExpect { status { isUnauthorized() } }

        verify(orderItemRepository, never()).findByReservationIdIn(any())
    }

    private fun givenOrders(vararg orders: Order) {
        whenever(orderRepository.findAllById(any())).thenReturn(orders.toList())
    }

    private fun givenItems(vararg items: OrderItem) {
        whenever(orderItemRepository.findByReservationIdIn(any())).thenReturn(items.toList())
    }

    private fun order(id: Long, status: OrderStatus, daysAgo: Long): Order {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        PATHS.getValue(status).forEach { order.updateStatus(it) }
        if (status == OrderStatus.REFUNDED) order.completeRefund()
        order.updatedAt = LocalDateTime.now().minusDays(daysAgo)
        ReflectionTestUtils.setField(order, "id", id)
        return order
    }

    private fun item(id: Long, orderId: Long, reservationId: String): OrderItem {
        val item = OrderItem.create(
            orderId = orderId,
            sellerId = 7L,
            productId = 100L + id,
            productName = "상품",
            productPrice = BigDecimal("1000"),
            quantity = 1,
            reservationId = reservationId
        )
        ReflectionTestUtils.setField(item, "id", id)
        return item
    }

    companion object {
        private const val PATH = "/internal/api/v1/orders/terminal-check"

        private val PATHS = mapOf(
            OrderStatus.CANCELLED to listOf(OrderStatus.CANCELLED),
            OrderStatus.REFUNDED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.DELIVERED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.PAID to listOf(OrderStatus.PAYING, OrderStatus.PAID)
        )
    }
}

private const val TERMINAL_TOKEN = "test-internal-token"
