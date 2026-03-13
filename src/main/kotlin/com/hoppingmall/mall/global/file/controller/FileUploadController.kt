package com.hoppingmall.mall.global.file.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.file.dto.request.FileUploadRequest
import com.hoppingmall.mall.global.file.dto.response.FileUploadResponse
import com.hoppingmall.mall.global.file.service.FileUploadService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "파일 업로드")
class FileUploadController(
    private val fileUploadService: FileUploadService
) {

    @PostMapping("/upload")
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("domain") domain: String
    ): ApiResponse<FileUploadResponse> {
        val request = FileUploadRequest(file, domain)
        val fileUploadResponse = fileUploadService.uploadFile(request)
        return ApiResponse.success(fileUploadResponse)
    }
} 