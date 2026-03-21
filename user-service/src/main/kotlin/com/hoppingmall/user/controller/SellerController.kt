package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.service.SellerCommandService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<Unit> {
        val userId = principal.getUserId()
        sellerCommandService.apply(userId, request)
        return ApiResponse.success(Unit)
    }
}
