package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.dto.request.ProductSearchCondition
import com.hoppingmall.product.product.dto.response.ProductResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface ProductQueryService {
    fun getProducts(pageable: Pageable): Slice<ProductResponse>

    fun getProductById(productId: Long): ProductResponse?

    fun getProductsBySellerId(sellerId: Long, pageable: Pageable): Slice<ProductResponse>

    fun getProductsByCategoryId(categoryId: Long, pageable: Pageable): Slice<ProductResponse>

    fun searchProducts(condition: ProductSearchCondition, pageable: Pageable): Slice<ProductResponse>
}