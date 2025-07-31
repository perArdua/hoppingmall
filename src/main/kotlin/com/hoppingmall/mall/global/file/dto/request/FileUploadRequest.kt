package com.hoppingmall.mall.global.file.dto.request

import org.springframework.web.multipart.MultipartFile

data class FileUploadRequest(
    val file: MultipartFile,
    val domain: String
) 