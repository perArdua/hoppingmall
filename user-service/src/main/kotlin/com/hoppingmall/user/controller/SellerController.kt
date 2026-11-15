package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.dto.response.SellerApplicationResponse
import com.hoppingmall.user.service.SellerCommandService
import com.hoppingmall.user.service.SellerQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sellers")
@Tag(name = "판매자 신청")
class SellerController(
    private val sellerCommandService: SellerCommandService,
    private val sellerQueryService: SellerQueryService
) {

    @PostMapping("/apply")
    fun applyForSeller(
        @RequestBody @Valid request: SellerApplyRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<SellerApplicationResponse> {
        val userId = principal.getUserId()
        return ApiResponse.success(sellerCommandService.apply(userId, request))
    }

    @GetMapping("/me")
    fun getMyApplication(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<SellerApplicationResponse> =
        ApiResponse.success(sellerQueryService.getMyApplication(principal.getUserId()))
}
