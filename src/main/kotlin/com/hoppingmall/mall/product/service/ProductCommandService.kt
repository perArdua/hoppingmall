package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.dto.request.ProductCreateRequest
import com.hoppingmall.mall.product.dto.request.ProductUpdateRequest
import com.hoppingmall.mall.product.dto.response.ProductResponse

interface ProductCommandService {
    fun createProduct(request: ProductCreateRequest): ProductResponse
    fun updateProduct(productId: Long, request: ProductUpdateRequest): ProductResponse
    fun deleteProduct(productId: Long)
} 