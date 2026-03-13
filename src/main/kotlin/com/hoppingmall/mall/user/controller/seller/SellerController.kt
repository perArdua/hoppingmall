package com.hoppingmall.mall.user.controller.seller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.seller.SellerApplyRequest
import com.hoppingmall.mall.user.service.seller.SellerCommandService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sellers")
@Tag(name = "판매자 신청")
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
        return ApiResponse.Companion.success(Unit)
    }
}