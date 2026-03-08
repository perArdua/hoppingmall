package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.product.domain.Product
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository: JpaRepository<Product, Long>, ProductSearchRepository {

    fun findBy(pageable: Pageable): Slice<Product>

    fun findBySellerId(sellerId: Long, pageable: Pageable): Slice<Product>

    fun findByCategoryId(categoryId: Long, pageable: Pageable): Slice<Product>

    fun findNullableById(id: Long): Product?
}
