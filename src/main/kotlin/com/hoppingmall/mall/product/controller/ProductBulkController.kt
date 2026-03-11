package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.product.dto.response.BulkImportProgressResponse
import com.hoppingmall.mall.product.dto.response.BulkRowError
import com.hoppingmall.mall.product.dto.response.BulkValidationResponse
import com.hoppingmall.mall.product.service.BulkImportService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/products/bulk")
class ProductBulkController(
    private val bulkImportService: BulkImportService
) {

    @PostMapping("/validate")
    fun validateCsv(
        @RequestParam("file") file: MultipartFile
    ): ApiResponse<BulkValidationResponse> {
        val response = bulkImportService.validate(file)
        return ApiResponse.success(response)
    }

    @PostMapping("/import")
    fun importCsv(
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ApiResponse<BulkImportProgressResponse> {
        val response = bulkImportService.startImport(userPrincipal.getUserId(), file)
        return ApiResponse.success(response)
    }

    @GetMapping("/{jobId}")
    fun getJobProgress(
        @PathVariable jobId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ApiResponse<BulkImportProgressResponse> {
        val response = bulkImportService.getJobProgress(jobId, userPrincipal.getUserId())
        return ApiResponse.success(response)
    }

    @GetMapping("/{jobId}/errors")
    fun getJobErrors(
        @PathVariable jobId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ApiResponse<List<BulkRowError>> {
        val response = bulkImportService.getJobErrors(jobId, userPrincipal.getUserId())
        return ApiResponse.success(response)
    }
}
