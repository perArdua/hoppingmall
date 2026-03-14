package com.hoppingmall.mall.product.api

interface ProductQueryPort {
    fun findById(id: Long): ProductInfo?
    fun findAllByIds(ids: List<Long>): List<ProductInfo>
    fun findMainImageUrl(productId: Long): String?
}
