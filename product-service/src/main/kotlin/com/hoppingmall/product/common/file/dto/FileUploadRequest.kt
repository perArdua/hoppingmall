package com.hoppingmall.product.common.file.dto

import org.springframework.web.multipart.MultipartFile

data class FileUploadRequest(
    val file: MultipartFile,
    val domain: String
)
