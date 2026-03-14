package com.hoppingmall.mall.membership.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.membership.dto.response.MembershipResponse
import com.hoppingmall.mall.membership.service.MembershipCommandService
import com.hoppingmall.mall.membership.service.MembershipQueryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "멤버십")
class MembershipController(
    private val membershipCommandService: MembershipCommandService,
    private val membershipQueryService: MembershipQueryService
) {

    @PostMapping
    fun createMembership(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<ApiResponse<MembershipResponse>> {
        val response = membershipCommandService.createMembership(userPrincipal.getUserId())
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    @GetMapping("/me")
    fun getMyMembership(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<ApiResponse<MembershipResponse>> {
        val response = membershipQueryService.getMembershipByUserId(userPrincipal.getUserId())
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @GetMapping("/users/{userId}")
    fun getMembershipByUserId(
        @PathVariable userId: Long
    ): ResponseEntity<ApiResponse<MembershipResponse>> {
        val response = membershipQueryService.getMembershipByUserId(userId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
