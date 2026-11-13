package com.hoppingmall.order.internal

import com.hoppingmall.order.config.SecurityConfig
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import io.micrometer.tracing.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 확인용 주문 내부 조회 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(controllers = [InternalOrderController::class], properties = ["internal.service.token=$TOKEN"])
@Import(SecurityConfig::class)
class InternalOrderSummaryApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var orderRepository: OrderRepository

    @MockitoBean
    private lateinit var orderItemRepository: OrderItemRepository

    @MockitoBean
    private lateinit var shippingRepository: ShippingRepository

    @MockitoBean
    private lateinit var refundRepository: RefundRepository

    @MockitoBean
    private lateinit var tracer: Tracer

    @Test
    fun 내부_토큰으로_주문을_조회하면_주문_id_구매자_총액_상태를_돌려준다() {
        val order = Order.create(buyerId = 7L, totalAmount = BigDecimal("15000"))
        ReflectionTestUtils.setField(order, "id", 42L)
        order.updateStatus(OrderStatus.PAYING)
        whenever(orderRepository.findById(42L)).thenReturn(Optional.of(order))

        mockMvc.get("/internal/api/v1/orders/42") {
            header("X-Internal-Token", TOKEN)
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("SUCCESS") }
            jsonPath("$.data.orderId") { value(42) }
            jsonPath("$.data.buyerId") { value(7) }
            jsonPath("$.data.totalAmount") { value(15000.00) }
            jsonPath("$.data.status") { value("PAYING") }
            jsonPath("$.data.length()") { value(4) }
        }
    }

    @Test
    fun 총액은_소수_둘째_자리까지의_숫자로_돌려준다() {
        val order = Order.create(buyerId = 7L, totalAmount = BigDecimal("12345.5"))
        ReflectionTestUtils.setField(order, "id", 43L)
        whenever(orderRepository.findById(43L)).thenReturn(Optional.of(order))

        val body = mockMvc.get("/internal/api/v1/orders/43") {
            header("X-Internal-Token", TOKEN)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CREATED") }
        }.andReturn().response.contentAsString

        assertThat(body).contains("\"totalAmount\":12345.50")
    }

    @Test
    fun 없는_주문이면_404() {
        whenever(orderRepository.findById(99L)).thenReturn(Optional.empty())

        mockMvc.get("/internal/api/v1/orders/99") {
            header("X-Internal-Token", TOKEN)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORD001") }
        }
    }

    @Test
    fun 내부_토큰이_없으면_401이고_주문을_읽지_않는다() {
        mockMvc.get("/internal/api/v1/orders/42")
            .andExpect { status { isUnauthorized() } }

        verify(orderRepository, never()).findById(42L)
    }

    @Test
    fun 내부_토큰이_틀리면_401이고_주문을_읽지_않는다() {
        mockMvc.get("/internal/api/v1/orders/42") {
            header("X-Internal-Token", "wrong-token")
        }.andExpect { status { isUnauthorized() } }

        verify(orderRepository, never()).findById(42L)
    }
}

private const val TOKEN = "test-internal-token"
