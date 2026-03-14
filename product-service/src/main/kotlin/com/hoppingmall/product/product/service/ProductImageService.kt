package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.dto.response.ProductImageResponse
import org.springframework.web.multipart.MultipartFile

interface ProductImageService {
    fun uploadProductImage(imageFile: MultipartFile): ProductImageResponse
} 