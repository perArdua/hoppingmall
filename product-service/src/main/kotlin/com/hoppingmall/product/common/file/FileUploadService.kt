package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.dto.FileUploadResponse

interface FileUploadService {
    fun uploadFile(request: FileUploadRequest): FileUploadResponse
}
