package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.page.PageRequestLimits
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.dto.response.SellerApplicationPageResponse
import com.hoppingmall.user.service.AdminCommandService
import com.hoppingmall.user.service.SellerQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "관리자")
class AdminController(
    private val adminCommandService: AdminCommandService,
    private val sellerQueryService: SellerQueryService
) {

    @GetMapping("/sellers")
    fun getSellerApplications(
        @RequestParam(required = false) status: Seller.ApprovalStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String? = null
    ): ApiResponse<SellerApplicationPageResponse> {
        PageRequestLimits.requireValid(page, size)
        return ApiResponse.success(sellerQueryService.getApplications(status, page, size, keyword))
    }

    @PatchMapping("/sellers/{id}/approve")
    fun updateSellerApprovalStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: SellerApprovalRequest
    ): ApiResponse<Unit> {
        adminCommandService.updateSellerApprovalStatus(id, request)
        return ApiResponse.success(Unit)
    }
}
