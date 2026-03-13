package com.hoppingmall.mall.global.file.service

import com.hoppingmall.mall.global.file.dto.request.FileUploadRequest
import com.hoppingmall.mall.global.file.dto.response.FileUploadResponse

interface FileUploadService {
    fun uploadFile(request: FileUploadRequest): FileUploadResponse
} 