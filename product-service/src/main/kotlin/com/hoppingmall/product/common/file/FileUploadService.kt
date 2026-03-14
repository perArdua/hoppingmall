package com.hoppingmall.product.common.file

import org.springframework.web.multipart.MultipartFile

interface FileUploadService {
    fun uploadFile(request: FileUploadRequest): FileUploadResponse
}

data class FileUploadRequest(
    val file: MultipartFile,
    val domain: String
)

data class FileUploadResponse(
    val fileUrl: String,
    val fileName: String,
    val fileSize: Long,
    val domain: String
)
