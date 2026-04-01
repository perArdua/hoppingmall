package com.hoppingmall.product.common.file.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.product.common.file.FileUploadService
import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.dto.FileUploadResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "파일 업로드")
@RestController
@RequestMapping("/api/v1/files")
class FileUploadController(
    private val fileUploadService: FileUploadService
) {

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("domain") domain: String
    ): ApiResponse<FileUploadResponse> {
        val request = FileUploadRequest(file = file, domain = domain)
        val response = fileUploadService.uploadFile(request)
        return ApiResponse.success(response)
    }
}
