package com.hoppingmall.mall.point.controller

import com.hoppingmall.mall.point.dto.request.PointUseRequest
import com.hoppingmall.mall.point.dto.response.PointBalanceResponse
import com.hoppingmall.mall.point.dto.response.PointHistoryResponse
import com.hoppingmall.mall.point.dto.response.PointUseResponse
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.point.service.PointCommandService
import com.hoppingmall.mall.point.service.PointQueryService
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
import org.springframework.http.ResponseEntity
import org.springframework.security.core.userdetails.UserDetails
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PointController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointControllerTest {

    private val pointQueryService: PointQueryService = mock()
    private val pointCommandService: PointCommandService = mock()
    private val pointController = PointController(pointQueryService, pointCommandService)

    @Nested
    @DisplayName("getMyPointBalance")
    inner class GetMyPointBalance {
        @Test
        fun 포인트_잔액_조회_성공() {
            val userId = 1L
            val balance = BigDecimal("1000")
            val userDetails: UserDetails = mock()
            val balanceResponse = PointBalanceResponse(balance)

            whenever(userDetails.username).thenReturn(userId.toString())
            whenever(pointQueryService.getPointBalance(userId)).thenReturn(balanceResponse)

            val result = pointController.getMyPointBalance(userDetails)

            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(balanceResponse, result.body)
            verify(pointQueryService).getPointBalance(userId)
        }
    }

    @Nested
    @DisplayName("getMyPointHistory")
    inner class GetMyPointHistory {
        @Test
        fun 포인트_내역_조회_성공() {
            val userId = 1L
            val userDetails: UserDetails = mock()
            val page = 0
            val size = 10
            val pageable = PageRequest.of(page, size)

            val historyResponse = PointHistoryResponse(
                id = 1L,
                userId = userId,
                amount = BigDecimal("100"),
                type = PointType.EARN,
                reason = "결제 완료",
                orderId = 1L,
                paymentId = 1L,
                createdAt = LocalDateTime.now()
            )

            val histories = listOf(historyResponse)
            val pageResponse = PageImpl(histories, pageable, 1)

            whenever(userDetails.username).thenReturn(userId.toString())
            whenever(pointQueryService.getPointHistory(userId, pageable)).thenReturn(pageResponse)

            val result = pointController.getMyPointHistory(userDetails, page, size)

            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(pageResponse, result.body)
            verify(pointQueryService).getPointHistory(userId, pageable)
        }
    }

    @Nested
    @DisplayName("usePoint")
    inner class UsePoint {
        @Test
        fun 포인트_사용_성공() {
            val userId = 1L
            val request = PointUseRequest(
                amount = BigDecimal("500"),
                orderId = 1L,
                reason = "상품 구매"
            )
            val userDetails: UserDetails = mock()
            val useResponse = PointUseResponse(
                usedAmount = BigDecimal("500"),
                remainingBalance = BigDecimal("500"),
                orderId = 1L
            )

            whenever(userDetails.username).thenReturn(userId.toString())
            whenever(pointCommandService.usePoint(userId, request)).thenReturn(useResponse)

            val result = pointController.usePoint(request, userDetails)

            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(useResponse, result.body)
            verify(pointCommandService).usePoint(userId, request)
        }
    }
} 