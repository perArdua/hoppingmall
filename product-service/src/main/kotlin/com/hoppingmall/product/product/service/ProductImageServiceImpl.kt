package com.hoppingmall.product.product.service

import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.FileUploadService
import com.hoppingmall.product.product.dto.response.ProductImageResponse
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ProductImageServiceImpl(
    private val fileUploadService: FileUploadService
) : ProductImageService {

    override fun uploadProductImage(imageFile: MultipartFile): ProductImageResponse {
        val request = FileUploadRequest(imageFile, "product")
        val fileUploadResponse = fileUploadService.uploadFile(request)
        
        return ProductImageResponse(
            imageUrl = fileUploadResponse.fileUrl,
            fileName = fileUploadResponse.fileName,
            fileSize = fileUploadResponse.fileSize
        )
    }
} 