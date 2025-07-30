package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.product.domain.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository: JpaRepository<Product, Long> {

    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<Product>

    fun findNullableById(id: Long): Product?
}