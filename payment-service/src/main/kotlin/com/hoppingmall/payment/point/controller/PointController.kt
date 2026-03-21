package com.hoppingmall.payment.point.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.payment.point.dto.request.PointUseRequest
import com.hoppingmall.payment.point.dto.response.PointBalanceResponse
import com.hoppingmall.payment.point.dto.response.PointHistoryResponse
import com.hoppingmall.payment.point.dto.response.PointUseResponse
import com.hoppingmall.payment.point.service.PointCommandService
import com.hoppingmall.payment.point.service.PointQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import com.hoppingmall.common.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/points")
@Tag(name = "포인트")
class PointController(
    private val pointQueryService: PointQueryService,
    private val pointCommandService: PointCommandService
) {

    @GetMapping("/my-balance")
    fun getMyPointBalance(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<PointBalanceResponse> {
        val userId = principal.getUserId()
        val balance = pointQueryService.getPointBalance(userId)
        return ApiResponse.success(balance)
    }

    @GetMapping("/my-history")
    fun getMyPointHistory(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<Slice<PointHistoryResponse>> {
        val userId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        val history = pointQueryService.getPointHistory(userId, pageable)
        return ApiResponse.success(history)
    }

    @PostMapping("/use")
    fun usePoint(
        @Valid @RequestBody request: PointUseRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<PointUseResponse> {
        val userId = principal.getUserId()
        val result = pointCommandService.usePoint(userId, request)
        return ApiResponse.success(result)
    }
}
