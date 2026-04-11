package com.hoppingmall.order.refund.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.refund.service.RefundCommandService
import com.hoppingmall.order.refund.service.RefundQueryService
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
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("RefundController")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundControllerTest {

    @Mock
    private lateinit var refundCommandService: RefundCommandService

    @Mock
    private lateinit var refundQueryService: RefundQueryService

    @InjectMocks
    private lateinit var controller: RefundController

    private val buyerPrincipal = UserPrincipal.of(1L, "BUYER")
    private val sellerPrincipal = UserPrincipal.of(5L, "SELLER")

    private fun createRefundResponse(
        id: Long = 1L,
        status: RefundStatus = RefundStatus.REQUESTED
    ): RefundResponse {
        return RefundResponse(
            id = id,
            orderId = 10L,
            paymentId = 20L,
            buyerId = 1L,
            sellerId = 5L,
            status = status,
            reason = RefundReason.CHANGE_OF_MIND,
            reasonDetail = null,
            refundAmount = BigDecimal("10000"),
            isFullRefund = false,
            rejectionReason = null,
            approvedBy = null,
            completedAt = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            items = emptyList()
        )
    }

    @Test
    fun 환불을_요청한다() {
        val request = RefundCreateRequest(
            orderId = 10L,
            reason = RefundReason.CHANGE_OF_MIND,
            reasonDetail = null,
            items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
        )
        val response = createRefundResponse()

        whenever(refundCommandService.requestRefund(1L, request)).thenReturn(response)

        val result = controller.requestRefund(request, buyerPrincipal)

        assertThat(result.data!!.id).isEqualTo(1L)
    }

    @Test
    fun 환불을_단건_조회한다() {
        val response = createRefundResponse()

        whenever(refundQueryService.getRefund(1L, 1L)).thenReturn(response)

        val result = controller.getRefund(1L, buyerPrincipal)

        assertThat(result.data!!.id).isEqualTo(1L)
    }

    @Test
    fun 내_환불_목록을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val refunds = listOf(createRefundResponse())
        val slice = SliceImpl(refunds, pageable, false)

        whenever(refundQueryService.getMyRefunds(1L, pageable)).thenReturn(slice)

        val result = controller.getMyRefunds(buyerPrincipal, 0, 10)

        assertThat(result.data!!.content).hasSize(1)
    }

    @Test
    fun 판매자_환불_목록을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val refunds = listOf(createRefundResponse())
        val slice = SliceImpl(refunds, pageable, false)

        whenever(refundQueryService.getSellerRefunds(5L, pageable)).thenReturn(slice)

        val result = controller.getSellerRefunds(sellerPrincipal, 0, 10)

        assertThat(result.data!!.content).hasSize(1)
    }

    @Test
    fun 환불을_승인한다() {
        val response = createRefundResponse(status = RefundStatus.COMPLETED)

        whenever(refundCommandService.approveRefund(1L, 5L)).thenReturn(response)

        val result = controller.approveRefund(1L, sellerPrincipal)

        assertThat(result.data!!.status).isEqualTo(RefundStatus.COMPLETED)
    }

    @Test
    fun 환불을_거절한다() {
        val request = RefundApprovalRequest(rejectionReason = "사유 불충분")
        val response = createRefundResponse(status = RefundStatus.REJECTED)

        whenever(refundCommandService.rejectRefund(1L, 5L, request)).thenReturn(response)

        val result = controller.rejectRefund(1L, request, sellerPrincipal)

        assertThat(result.data!!.status).isEqualTo(RefundStatus.REJECTED)
    }
}
