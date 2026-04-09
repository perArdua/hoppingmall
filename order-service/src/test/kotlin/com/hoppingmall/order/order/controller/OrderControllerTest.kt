package com.hoppingmall.order.order.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.service.OrderCommandService
import com.hoppingmall.order.order.service.OrderQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("OrderController")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderControllerTest {

    @Mock
    private lateinit var orderCommandService: OrderCommandService

    @Mock
    private lateinit var orderQueryService: OrderQueryService

    @InjectMocks
    private lateinit var controller: OrderController

    private val buyerPrincipal = UserPrincipal.of(1L, "BUYER")
    private val adminPrincipal = UserPrincipal.of(99L, "ADMIN")

    private fun createOrderResponse(
        id: Long = 1L,
        status: OrderStatus = OrderStatus.PAYING
    ): OrderResponse {
        return OrderResponse(
            id = id,
            buyerId = 1L,
            status = status,
            totalAmount = BigDecimal("50000"),
            items = emptyList(),
            createdAt = LocalDateTime.now()
        )
    }

    @Test
    fun 주문을_생성한다() {
        val request = OrderCreateRequest(cartItemIds = listOf(1L, 2L))
        val response = createOrderResponse()

        whenever(orderCommandService.createOrder(1L, request)).thenReturn(response)

        val result = controller.createOrder(buyerPrincipal, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(result.body!!.data!!.id).isEqualTo(1L)
    }

    @Test
    fun 주문을_단건_조회한다() {
        val response = createOrderResponse()

        whenever(orderQueryService.getOrder(1L, 1L)).thenReturn(response)

        val result = controller.getOrder(buyerPrincipal, 1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.id).isEqualTo(1L)
    }

    @Test
    fun 내_주문_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val orders = listOf(createOrderResponse())
        val slice = SliceImpl(orders, pageable, false)

        whenever(orderQueryService.getMyOrders(1L, pageable)).thenReturn(slice)

        val result = controller.getMyOrders(buyerPrincipal, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.content).hasSize(1)
    }

    @Test
    fun 주문을_취소한다() {
        val response = createOrderResponse(status = OrderStatus.CANCELLED)

        whenever(orderCommandService.cancelOrder(1L, 1L)).thenReturn(response)

        val result = controller.cancelOrder(buyerPrincipal, 1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun 관리자가_주문_상태를_변경한다() {
        val request = OrderStatusUpdateRequest(status = OrderStatus.SHIPPED)
        val response = createOrderResponse(status = OrderStatus.SHIPPED)

        whenever(orderCommandService.updateOrderStatus(1L, request, 99L, true)).thenReturn(response)

        val result = controller.updateOrderStatus(adminPrincipal, 1L, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.status).isEqualTo(OrderStatus.SHIPPED)
    }
}
