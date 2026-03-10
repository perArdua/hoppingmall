package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.product.domain.ProductImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductImageRepository: JpaRepository<ProductImage, Long> {

    fun findByProductIdOrderBySortOrder(productId: Long): List<ProductImage>

    fun findByProductIdIn(productIds: List<Long>): List<ProductImage>
}
