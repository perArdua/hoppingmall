package com.hoppingmall.mall.point.controller

import com.hoppingmall.mall.global.idempotency.Idempotent
import com.hoppingmall.mall.point.dto.request.PointUseRequest
import com.hoppingmall.mall.point.dto.response.PointBalanceResponse
import com.hoppingmall.mall.point.dto.response.PointHistoryResponse
import com.hoppingmall.mall.point.dto.response.PointUseResponse
import com.hoppingmall.mall.point.service.PointCommandService
import com.hoppingmall.mall.point.service.PointQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import com.hoppingmall.mall.global.auth.UserPrincipal
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
    ): ResponseEntity<PointBalanceResponse> {
        val userId = principal.getUserId()
        val balance = pointQueryService.getPointBalance(userId)
        return ResponseEntity.ok(balance)
    }
    
    @GetMapping("/my-history")
    fun getMyPointHistory(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<PointHistoryResponse>> {
        val userId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        val history = pointQueryService.getPointHistory(userId, pageable)
        return ResponseEntity.ok(history)
    }
    
    @Idempotent(ttlHours = 24)
    @PostMapping("/use")
    fun usePoint(
        @Valid @RequestBody request: PointUseRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PointUseResponse> {
        val userId = principal.getUserId()
        val result = pointCommandService.usePoint(userId, request)
        return ResponseEntity.ok(result)
    }
} 