package com.hoppingmall.mall.point.controller

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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/points")
class PointController(
    private val pointQueryService: PointQueryService,
    private val pointCommandService: PointCommandService
) {
    
    @GetMapping("/my-balance")
    fun getMyPointBalance(
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<PointBalanceResponse> {
        val userId = userDetails.username.toLong()
        val balance = pointQueryService.getPointBalance(userId)
        return ResponseEntity.ok(balance)
    }
    
    @GetMapping("/my-history")
    fun getMyPointHistory(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<PointHistoryResponse>> {
        val userId = userDetails.username.toLong()
        val pageable = PageRequest.of(page, size)
        val history = pointQueryService.getPointHistory(userId, pageable)
        return ResponseEntity.ok(history)
    }
    
    @PostMapping("/use")
    fun usePoint(
        @Valid @RequestBody request: PointUseRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<PointUseResponse> {
        val userId = userDetails.username.toLong()
        val result = pointCommandService.usePoint(userId, request)
        return ResponseEntity.ok(result)
    }
} 