package com.hoppingmall.mall.order.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.order.dto.request.OrderCreateRequest
import com.hoppingmall.mall.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.mall.order.dto.response.OrderItemResponse
import com.hoppingmall.mall.order.dto.response.OrderResponse
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.service.OrderCommandService
import com.hoppingmall.mall.order.service.OrderQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("OrderController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderControllerTest {

    private val orderCommandService: OrderCommandService = mock()
    private val orderQueryService: OrderQueryService = mock()
    private val controller = OrderController(orderCommandService, orderQueryService)

    private val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
    private val now = LocalDateTime.now()

    private fun createOrderResponse(
        id: Long = 1L,
        status: OrderStatus = OrderStatus.CREATED
    ): OrderResponse {
        return OrderResponse(
            id = id,
            buyerId = 1L,
            status = status,
            totalAmount = BigDecimal("50000"),
            items = listOf(
                OrderItemResponse(
                    id = 1L,
                    productId = 100L,
                    productName = "테스트 상품",
                    productPrice = BigDecimal("25000"),
                    quantity = 2,
                    totalPrice = BigDecimal("50000")
                )
            ),
            createdAt = now
        )
    }

    @Nested
    @DisplayName("createOrder")
    inner class CreateOrder {
        @Test
        fun 주문_생성_성공() {
            // given
            val request = OrderCreateRequest(cartItemIds = listOf(1L, 2L))
            val expectedResponse = createOrderResponse()

            whenever(orderCommandService.createOrder(userPrincipal.getUserId(), request)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<OrderResponse>> = controller.createOrder(userPrincipal, request)

            // then
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(orderCommandService).createOrder(userPrincipal.getUserId(), request)
        }
    }

    @Nested
    @DisplayName("getOrder")
    inner class GetOrder {
        @Test
        fun 주문_상세_조회_성공() {
            // given
            val orderId = 1L
            val expectedResponse = createOrderResponse()

            whenever(orderQueryService.getOrder(orderId, userPrincipal.getUserId())).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<OrderResponse>> = controller.getOrder(userPrincipal, orderId)

            // then
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(orderQueryService).getOrder(orderId, userPrincipal.getUserId())
        }
    }

    @Nested
    @DisplayName("getMyOrders")
    inner class GetMyOrders {
        @Test
        fun 내_주문_목록_조회_성공() {
            // given
            val pageable = PageRequest.of(0, 20)
            val expectedPage = PageImpl(listOf(createOrderResponse()), pageable, 1)

            whenever(orderQueryService.getMyOrders(userPrincipal.getUserId(), pageable)).thenReturn(expectedPage)

            // when
            val response = controller.getMyOrders(userPrincipal, pageable)

            // then
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(1, response.body?.data?.totalElements)
            verify(orderQueryService).getMyOrders(userPrincipal.getUserId(), pageable)
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    inner class CancelOrder {
        @Test
        fun 주문_취소_성공() {
            // given
            val orderId = 1L
            val expectedResponse = createOrderResponse(status = OrderStatus.CANCELLED)

            whenever(orderCommandService.cancelOrder(userPrincipal.getUserId(), orderId)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<OrderResponse>> = controller.cancelOrder(userPrincipal, orderId)

            // then
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(OrderStatus.CANCELLED, response.body?.data?.status)
            verify(orderCommandService).cancelOrder(userPrincipal.getUserId(), orderId)
        }
    }

    @Nested
    @DisplayName("updateOrderStatus")
    inner class UpdateOrderStatus {
        @Test
        fun 주문_상태_변경_성공() {
            // given
            val orderId = 1L
            val request = OrderStatusUpdateRequest(status = OrderStatus.PAID)
            val expectedResponse = createOrderResponse(status = OrderStatus.PAID)

            whenever(orderCommandService.updateOrderStatus(orderId, request)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<OrderResponse>> = controller.updateOrderStatus(orderId, request)

            // then
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(OrderStatus.PAID, response.body?.data?.status)
            verify(orderCommandService).updateOrderStatus(orderId, request)
        }
    }
}
