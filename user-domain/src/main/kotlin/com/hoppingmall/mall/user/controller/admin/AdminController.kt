package com.hoppingmall.mall.user.controller.admin

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.service.admin.AdminCommandService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.tags.Tag
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
        return ApiResponse.Companion.success(Unit)
    }
}