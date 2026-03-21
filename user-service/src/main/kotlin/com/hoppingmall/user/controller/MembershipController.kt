package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.dto.response.MembershipResponse
import com.hoppingmall.user.service.MembershipCommandService
import com.hoppingmall.user.service.MembershipQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
