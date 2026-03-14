package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.ProductImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductImageRepository: JpaRepository<ProductImage, Long> {

    fun findByProductIdOrderBySortOrder(productId: Long): List<ProductImage>

    fun findByProductIdIn(productIds: List<Long>): List<ProductImage>
}
