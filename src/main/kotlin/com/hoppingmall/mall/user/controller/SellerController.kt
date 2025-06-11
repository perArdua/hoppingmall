package com.hoppingmall.mall.user.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.SellerApplyRequest
import com.hoppingmall.mall.user.service.seller.SellerCommandService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/sellers")
class SellerController(
    private val sellerCommandService: SellerCommandService
) {

    @PostMapping("/apply")
    fun applyForSeller(
        @RequestBody @Valid request: SellerApplyRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ApiResponse<Unit> {
        val userId = userDetails.username.toLong()
        sellerCommandService.apply(userId, request)
        return ApiResponse.success(Unit)
    }
}
