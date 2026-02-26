package com.hoppingmall.mall.review.domain.repository

import com.hoppingmall.mall.review.domain.Review
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReviewRepository : JpaRepository<Review, Long> {
    fun findByProductId(productId: Long, pageable: Pageable): Page<Review>
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Page<Review>
    fun existsByOrderItemId(orderItemId: Long): Boolean
    fun findNullableById(id: Long): Review?

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId AND r.deletedAt IS NULL")
    fun averageRatingByProductId(@Param("productId") productId: Long): Double?

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.deletedAt IS NULL")
    fun countByProductId(@Param("productId") productId: Long): Long
}
