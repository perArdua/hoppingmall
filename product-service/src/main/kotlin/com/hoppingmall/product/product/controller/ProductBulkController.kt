package com.hoppingmall.product.product.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.product.dto.response.BulkImportProgressResponse
import com.hoppingmall.product.product.dto.response.BulkRowError
import com.hoppingmall.product.product.dto.response.BulkValidationResponse
import com.hoppingmall.product.product.service.BulkImportService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/products/bulk")
@Tag(name = "상품 대량 등록")
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
