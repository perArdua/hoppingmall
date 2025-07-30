package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductQueryService {
    fun getProducts(pageable: Pageable): Page<Product>
    
    fun getProductById(productId: Long): Product
    
    fun getProductsBySellerId(sellerId: Long, pageable: Pageable): Page<Product>
}