package com.hoppingmall.order.shipping.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.dto.response.ShippingResponse
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.service.ShippingCommandService
import com.hoppingmall.order.shipping.service.ShippingQueryService
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
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@DisplayName("ShippingController")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ShippingControllerTest {

    @Mock
    private lateinit var shippingCommandService: ShippingCommandService

    @Mock
    private lateinit var shippingQueryService: ShippingQueryService

    @InjectMocks
    private lateinit var controller: ShippingController

    private val sellerPrincipal = UserPrincipal.of(5L, "SELLER")

    private fun createShippingResponse(
        id: Long = 1L,
        status: ShippingStatus = ShippingStatus.PREPARING
    ): ShippingResponse {
        return ShippingResponse(
            id = id,
            orderId = 10L,
            buyerId = 1L,
            status = status,
            carrierName = "CJ대한통운",
            trackingNumber = "1234567890",
            recipientName = "홍길동",
            recipientPhone = "010-1234-5678",
            recipientAddress = "서울시 강남구",
            createdAt = LocalDateTime.now(),
            updatedAt = null
        )
    }

    @Test
    fun 배송을_생성한다() {
        val request = ShippingCreateRequest(
            orderId = 10L, carrierName = "CJ대한통운",
            trackingNumber = "1234567890", recipientName = "홍길동",
            recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
        )
        val response = createShippingResponse()

        whenever(shippingCommandService.createShipping(5L, request)).thenReturn(response)

        val result = controller.createShipping(sellerPrincipal, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(result.body!!.data!!.id).isEqualTo(1L)
    }

    @Test
    fun 배송_상태를_변경한다() {
        val request = ShippingStatusUpdateRequest(status = ShippingStatus.IN_TRANSIT)
        val response = createShippingResponse(status = ShippingStatus.IN_TRANSIT)

        whenever(shippingCommandService.updateShippingStatus(5L, 1L, request)).thenReturn(response)

        val result = controller.updateShippingStatus(sellerPrincipal, 1L, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.status).isEqualTo(ShippingStatus.IN_TRANSIT)
    }

    @Test
    fun 주문ID로_배송을_조회한다() {
        val response = createShippingResponse()

        whenever(shippingQueryService.getShippingByOrderId(10L)).thenReturn(response)

        val result = controller.getShippingByOrderId(10L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.orderId).isEqualTo(10L)
    }

    @Test
    fun 배송ID로_배송을_조회한다() {
        val response = createShippingResponse()

        whenever(shippingQueryService.getShipping(1L)).thenReturn(response)

        val result = controller.getShipping(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.id).isEqualTo(1L)
    }
}
