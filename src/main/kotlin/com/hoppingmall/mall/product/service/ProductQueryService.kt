package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.dto.response.ProductResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductQueryService {
    fun getProducts(pageable: Pageable): Page<ProductResponse>
    
    fun getProductById(productId: Long): ProductResponse
    
    fun getProductsBySellerId(sellerId: Long, pageable: Pageable): Page<ProductResponse>
}