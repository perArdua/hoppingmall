package com.hoppingmall.mall.shipping.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.mall.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.mall.shipping.dto.response.ShippingResponse
import com.hoppingmall.mall.shipping.enum.ShippingStatus
import com.hoppingmall.mall.shipping.service.ShippingCommandService
import com.hoppingmall.mall.shipping.service.ShippingQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@DisplayName("ShippingController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ShippingControllerTest {

    private val shippingCommandService: ShippingCommandService = mock()
    private val shippingQueryService: ShippingQueryService = mock()
    private val controller = ShippingController(shippingCommandService, shippingQueryService)

    private val sellerPrincipal = UserPrincipal(10L, "seller@example.com", "SELLER")
    private val now = LocalDateTime.now()

    private fun createShippingResponse(
        id: Long = 1L,
        status: ShippingStatus = ShippingStatus.PREPARING
    ): ShippingResponse {
        return ShippingResponse(
            id = id,
            orderId = 1L,
            buyerId = 1L,
            status = status,
            carrierName = "CJ대한통운",
            trackingNumber = "1234567890",
            recipientName = "홍길동",
            recipientPhone = "010-1234-5678",
            recipientAddress = "서울시 강남구 테헤란로 123",
            createdAt = now,
            updatedAt = null
        )
    }

    @Nested
    @DisplayName("createShipping")
    inner class CreateShipping {
        @Test
        fun 송장_등록_성공() {
            // given
            val request = ShippingCreateRequest(
                orderId = 1L,
                carrierName = "CJ대한통운",
                trackingNumber = "1234567890",
                recipientName = "홍길동",
                recipientPhone = "010-1234-5678",
                recipientAddress = "서울시 강남구 테헤란로 123"
            )
            val expectedResponse = createShippingResponse()

            whenever(shippingCommandService.createShipping(sellerPrincipal.getUserId(), request))
                .thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<ShippingResponse>> =
                controller.createShipping(sellerPrincipal, request)

            // then
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(shippingCommandService).createShipping(sellerPrincipal.getUserId(), request)
        }
    }

    @Nested
    @DisplayName("updateShippingStatus")
    inner class UpdateShippingStatus {
        @Test
        fun 배송_상태_변경_성공() {
            // given
            val shippingId = 1L
            val request = ShippingStatusUpdateRequest(status = ShippingStatus.IN_TRANSIT)
            val expectedResponse = createShippingResponse(status = ShippingStatus.IN_TRANSIT)

            whenever(shippingCommandService.updateShippingStatus(sellerPrincipal.getUserId(), shippingId, request))
                .thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<ShippingResponse>> =
                controller.updateShippingStatus(sellerPrincipal, shippingId, request)

            // then
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(ShippingStatus.IN_TRANSIT, response.body?.data?.status)
            verify(shippingCommandService).updateShippingStatus(sellerPrincipal.getUserId(), shippingId, request)
        }
    }

    @Nested
    @DisplayName("getShippingByOrderId")
    inner class GetShippingByOrderId {
        @Test
        fun 주문별_배송_조회_성공() {
            // given
            val orderId = 1L
            val expectedResponse = createShippingResponse()

            whenever(shippingQueryService.getShippingByOrderId(orderId)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<ShippingResponse>> =
                controller.getShippingByOrderId(orderId)

            // then
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(shippingQueryService).getShippingByOrderId(orderId)
        }
    }

    @Nested
    @DisplayName("getShipping")
    inner class GetShipping {
        @Test
        fun 배송_상세_조회_성공() {
            // given
            val shippingId = 1L
            val expectedResponse = createShippingResponse()

            whenever(shippingQueryService.getShipping(shippingId)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<ShippingResponse>> =
                controller.getShipping(shippingId)

            // then
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(shippingQueryService).getShipping(shippingId)
        }
    }
}
