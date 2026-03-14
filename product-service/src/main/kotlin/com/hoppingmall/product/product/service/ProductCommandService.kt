package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.dto.response.ProductResponse

interface ProductCommandService {
    fun createProduct(request: ProductCreateRequest): ProductResponse
    fun updateProduct(productId: Long, request: ProductUpdateRequest): ProductResponse
    fun deleteProduct(productId: Long)
} 