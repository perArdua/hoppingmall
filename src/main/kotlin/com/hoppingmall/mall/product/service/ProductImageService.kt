package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.dto.response.ProductImageResponse
import org.springframework.web.multipart.MultipartFile

interface ProductImageService {
    fun uploadProductImage(imageFile: MultipartFile): ProductImageResponse
} 