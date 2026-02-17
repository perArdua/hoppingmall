package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface ProductRepository: JpaRepository<Product, Long> {

    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<Product>

    fun findByCategoryId(categoryId: Long, pageable: Pageable): Page<Product>

    fun findNullableById(id: Long): Product?

    @Query("""
        SELECT p FROM Product p
        WHERE (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%'))
        AND (:categoryId IS NULL OR p.categoryId = :categoryId)
        AND (:status IS NULL OR p.status = :status)
        AND (:minPrice IS NULL OR p.price >= :minPrice)
        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        AND p.deletedAt IS NULL
    """)
    fun searchProducts(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        pageable: Pageable
    ): Page<Product>
}