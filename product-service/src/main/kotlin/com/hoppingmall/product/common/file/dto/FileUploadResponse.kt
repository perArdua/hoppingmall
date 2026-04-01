package com.hoppingmall.product.common.file.dto

data class FileUploadResponse(
    val fileUrl: String,
    val fileName: String,
    val fileSize: Long,
    val domain: String
)
