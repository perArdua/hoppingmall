package com.hoppingmall.order.internal

import com.hoppingmall.order.config.SecurityConfig
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
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
    private lateinit var tracer: Tracer

    @Test
    fun 주문_id_목록을_받아_끝난_주문_id만_돌려준다() {
        whenever(orderRepository.findTerminalOrderIds(any(), any(), any())).thenReturn(listOf(3L, 1L))

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"order_ids":[1,2,3]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("SUCCESS") }
            jsonPath("$.data.terminal_order_ids.length()") { value(2) }
            jsonPath("$.data.terminal_order_ids[0]") { value(1) }
            jsonPath("$.data.terminal_order_ids[1]") { value(3) }
        }
    }

    @Test
    fun 끝난_주문은_취소와_환불이_더는_없는_상태이면서_그_상태가_된_지_30일이_지난_주문이다() {
        whenever(orderRepository.findTerminalOrderIds(any(), any(), any())).thenReturn(emptyList())
        val before = LocalDateTime.now().minusDays(30)

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"order_ids":[5,5,6]}"""
        }.andExpect { status { isOk() } }

        val ids = argumentCaptor<Collection<Long>>()
        val statuses = argumentCaptor<Collection<OrderStatus>>()
        val updatedBefore = argumentCaptor<LocalDateTime>()
        verify(orderRepository).findTerminalOrderIds(ids.capture(), statuses.capture(), updatedBefore.capture())
        assertThat(ids.firstValue).containsExactly(5L, 6L)
        assertThat(statuses.firstValue).containsExactlyInAnyOrder(OrderStatus.CANCELLED, OrderStatus.REFUNDED)
        assertThat(updatedBefore.firstValue).isBetween(before.minusMinutes(1), before.plusMinutes(1))
    }

    @Test
    fun 빈_목록이면_조회하지_않고_빈_목록을_돌려준다() {
        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"order_ids":[]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.terminal_order_ids.length()") { value(0) }
        }

        verify(orderRepository, never()).findTerminalOrderIds(any(), any(), any())
    }

    @Test
    fun 주문_id가_500개를_넘으면_400이고_조회하지_않는다() {
        val ids = (1..501).joinToString(",")

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"order_ids":[$ids]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }

        verify(orderRepository, never()).findTerminalOrderIds(any(), any(), any())
    }

    @Test
    fun 주문_id가_500개면_받는다() {
        whenever(orderRepository.findTerminalOrderIds(any(), any(), any())).thenReturn(emptyList())
        val ids = (1..500).joinToString(",")

        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"order_ids":[$ids]}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun order_ids가_없으면_400이고_조회하지_않는다() {
        mockMvc.post(PATH) {
            header("X-Internal-Token", TERMINAL_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect { status { isBadRequest() } }

        verify(orderRepository, never()).findTerminalOrderIds(any(), any(), any())
    }

    @Test
    fun 내부_토큰이_없으면_401이고_주문을_읽지_않는다() {
        mockMvc.post(PATH) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"order_ids":[1]}"""
        }.andExpect { status { isUnauthorized() } }

        verify(orderRepository, never()).findTerminalOrderIds(any(), any(), any())
    }

    companion object {
        private const val PATH = "/internal/api/v1/orders/terminal-check"
    }
}

private const val TERMINAL_TOKEN = "test-internal-token"
