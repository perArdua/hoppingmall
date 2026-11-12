package com.hoppingmall.product.review.domain.repository

import com.hoppingmall.product.review.domain.Review
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReviewRepository : JpaRepository<Review, Long> {
    fun findByProductId(productId: Long, pageable: Pageable): Slice<Review>
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Slice<Review>
    fun existsByOrderItemId(orderItemId: Long): Boolean
    fun findNullableById(id: Long): Review?

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId")
    fun averageRatingByProductId(@Param("productId") productId: Long): Double?

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId")
    fun countByProductId(@Param("productId") productId: Long): Long

    @Modifying
    @Query(value = "DELETE FROM reviews WHERE order_item_id = :orderItemId AND deleted_at IS NOT NULL", nativeQuery = true)
    fun deleteSoftDeletedByOrderItemId(@Param("orderItemId") orderItemId: Long): Int
}
