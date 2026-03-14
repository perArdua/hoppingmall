package com.hoppingmall.user.controller

import com.hoppingmall.user.common.ApiResponse
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.service.AdminCommandService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "관리자")
class AdminController(
    private val adminCommandService: AdminCommandService
) {

    @PatchMapping("/sellers/{id}/approve")
    fun updateSellerApprovalStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: SellerApprovalRequest
    ): ApiResponse<Unit> {
        adminCommandService.updateSellerApprovalStatus(id, request)
        return ApiResponse.success(Unit)
    }
}
