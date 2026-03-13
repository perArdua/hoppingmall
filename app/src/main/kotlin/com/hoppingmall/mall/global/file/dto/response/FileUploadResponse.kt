package com.hoppingmall.mall.global.file.dto.response

data class FileUploadResponse(
    val fileUrl: String,
    val fileName: String,
    val fileSize: Long,
    val domain: String
) 