package com.hoppingmall.mall.user.controller.admin

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.service.admin.AdminCommandService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val adminCommandService: AdminCommandService
) {

    @PatchMapping("/sellers/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateSellerApprovalStatus(
        @PathVariable id: Long,
        @RequestBody request: SellerApprovalRequest
    ): ApiResponse<Unit> {
        adminCommandService.updateSellerApprovalStatus(id, request)
        return ApiResponse.Companion.success(Unit)
    }
}