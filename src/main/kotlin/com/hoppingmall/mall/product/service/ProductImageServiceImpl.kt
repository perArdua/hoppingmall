package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.global.file.dto.request.FileUploadRequest
import com.hoppingmall.mall.global.file.service.FileUploadService
import com.hoppingmall.mall.product.dto.response.ProductImageResponse
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