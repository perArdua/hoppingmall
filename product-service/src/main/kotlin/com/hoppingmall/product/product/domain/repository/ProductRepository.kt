package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.Product
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface ProductRepository: JpaRepository<Product, Long>, ProductSearchRepository {

    fun findBy(pageable: Pageable): Slice<Product>

    fun findBySellerId(sellerId: Long, pageable: Pageable): Slice<Product>

    fun findByCategoryId(categoryId: Long, pageable: Pageable): Slice<Product>

    fun findNullableById(id: Long): Product?

    @Query(value = "SELECT * FROM products WHERE id = :id", nativeQuery = true)
    fun findIncludingDeletedById(@Param("id") id: Long): Product?

    @Query(value = "SELECT id FROM products WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun lockIdForUpdate(@Param("id") id: Long): Number?

    @Modifying
    @Query(
        value = "UPDATE products SET review_count = :reviewCount, average_rating = :averageRating WHERE id = :id",
        nativeQuery = true
    )
    fun updateReviewSummary(
        @Param("id") id: Long,
        @Param("reviewCount") reviewCount: Long,
        @Param("averageRating") averageRating: BigDecimal
    ): Int

    @Query(value = "SELECT id FROM products WHERE id > :afterId ORDER BY id LIMIT :limit", nativeQuery = true)
    fun findIdsAfter(@Param("afterId") afterId: Long, @Param("limit") limit: Int): List<Number>
}
