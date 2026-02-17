package com.hoppingmall.mall.refund.controller

import com.hoppingmall.mall.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.mall.refund.dto.request.RefundCreateRequest
import com.hoppingmall.mall.refund.dto.request.RefundItemRequest
import com.hoppingmall.mall.refund.dto.response.RefundItemResponse
import com.hoppingmall.mall.refund.dto.response.RefundResponse
import com.hoppingmall.mall.refund.enum.RefundReason
import com.hoppingmall.mall.refund.enum.RefundStatus
import com.hoppingmall.mall.refund.service.RefundCommandService
import com.hoppingmall.mall.refund.service.RefundQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.UserDetails
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("RefundController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundControllerTest {

    private val refundCommandService: RefundCommandService = mock()
    private val refundQueryService: RefundQueryService = mock()
    private val refundController = RefundController(refundCommandService, refundQueryService)

    private fun createRefundResponse(
        id: Long = 1L,
        status: RefundStatus = RefundStatus.REQUESTED
    ): RefundResponse {
        return RefundResponse(
            id = id,
            orderId = 1L,
            paymentId = 1L,
            buyerId = 1L,
            sellerId = 2L,
            status = status,
            reason = RefundReason.CHANGE_OF_MIND,
            reasonDetail = null,
            refundAmount = BigDecimal("30000"),
            isFullRefund = true,
            rejectionReason = null,
            approvedBy = null,
            completedAt = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            items = listOf(
                RefundItemResponse(
                    id = 1L,
                    orderItemId = 1L,
                    productId = 100L,
                    productName = "테스트 상품",
                    productPrice = BigDecimal("15000"),
                    quantity = 2,
                    refundPrice = BigDecimal("30000")
                )
            )
        )
    }

    @Nested
    @DisplayName("requestRefund")
    inner class RequestRefund {
        @Test
        fun `환불_요청_성공`() {
            // given
            val userId = 1L
            val request = RefundCreateRequest(
                orderId = 1L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 2))
            )
            val response = createRefundResponse()
            val userDetails: UserDetails = mock()

            whenever(userDetails.username).thenReturn(userId.toString())
            whenever(refundCommandService.requestRefund(userId, request)).thenReturn(response)

            // when
            val result = refundController.requestRefund(request, userDetails)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(response, result.body)
            verify(refundCommandService).requestRefund(userId, request)
        }
    }

    @Nested
    @DisplayName("getRefund")
    inner class GetRefund {
        @Test
        fun `환불_상세_조회_성공`() {
            // given
            val refundId = 1L
            val response = createRefundResponse()

            whenever(refundQueryService.getRefund(refundId)).thenReturn(response)

            // when
            val result = refundController.getRefund(refundId)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(response, result.body)
            verify(refundQueryService).getRefund(refundId)
        }
    }

    @Nested
    @DisplayName("getMyRefunds")
    inner class GetMyRefunds {
        @Test
        fun `내_환불_목록_조회_성공`() {
            // given
            val userId = 1L
            val page = 0
            val size = 10
            val pageable = PageRequest.of(page, size)
            val userDetails: UserDetails = mock()
            val response = createRefundResponse()
            val pageResponse = PageImpl(listOf(response), pageable, 1)

            whenever(userDetails.username).thenReturn(userId.toString())
            whenever(refundQueryService.getMyRefunds(userId, pageable)).thenReturn(pageResponse)

            // when
            val result = refundController.getMyRefunds(userDetails, page, size)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(pageResponse, result.body)
            verify(refundQueryService).getMyRefunds(userId, pageable)
        }
    }

    @Nested
    @DisplayName("getSellerRefunds")
    inner class GetSellerRefunds {
        @Test
        fun `판매자_환불_목록_조회_성공`() {
            // given
            val sellerId = 2L
            val page = 0
            val size = 10
            val pageable = PageRequest.of(page, size)
            val userDetails: UserDetails = mock()
            val response = createRefundResponse()
            val pageResponse = PageImpl(listOf(response), pageable, 1)

            whenever(userDetails.username).thenReturn(sellerId.toString())
            whenever(refundQueryService.getSellerRefunds(sellerId, pageable)).thenReturn(pageResponse)

            // when
            val result = refundController.getSellerRefunds(userDetails, page, size)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(pageResponse, result.body)
            verify(refundQueryService).getSellerRefunds(sellerId, pageable)
        }
    }

    @Nested
    @DisplayName("approveRefund")
    inner class ApproveRefund {
        @Test
        fun `환불_승인_성공`() {
            // given
            val refundId = 1L
            val sellerId = 2L
            val response = createRefundResponse(status = RefundStatus.COMPLETED)
            val userDetails: UserDetails = mock()

            whenever(userDetails.username).thenReturn(sellerId.toString())
            whenever(refundCommandService.approveRefund(refundId, sellerId)).thenReturn(response)

            // when
            val result = refundController.approveRefund(refundId, userDetails)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(response, result.body)
            verify(refundCommandService).approveRefund(refundId, sellerId)
        }
    }

    @Nested
    @DisplayName("rejectRefund")
    inner class RejectRefund {
        @Test
        fun `환불_거절_성공`() {
            // given
            val refundId = 1L
            val sellerId = 2L
            val request = RefundApprovalRequest(rejectionReason = "반품 불가")
            val response = createRefundResponse(status = RefundStatus.REJECTED)
            val userDetails: UserDetails = mock()

            whenever(userDetails.username).thenReturn(sellerId.toString())
            whenever(refundCommandService.rejectRefund(refundId, sellerId, request)).thenReturn(response)

            // when
            val result = refundController.rejectRefund(refundId, request, userDetails)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(response, result.body)
            verify(refundCommandService).rejectRefund(refundId, sellerId, request)
        }
    }
}
