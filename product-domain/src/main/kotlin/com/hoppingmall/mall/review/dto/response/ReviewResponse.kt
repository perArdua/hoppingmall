package com.hoppingmall.mall.review.dto.response

import com.hoppingmall.mall.review.domain.Review
import java.time.LocalDateTime

data class ReviewResponse(
    val id: Long,
    val buyerId: Long,
    val orderItemId: Long,
    val productId: Long,
    val rating: Int,
    val content: String,
    val imageUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(review: Review): ReviewResponse {
            return ReviewResponse(
                id = review.id!!,
                buyerId = review.buyerId,
                orderItemId = review.orderItemId,
                productId = review.productId,
                rating = review.rating,
                content = review.content,
                imageUrl = review.imageUrl,
                createdAt = review.createdAt,
                updatedAt = review.updatedAt ?: review.createdAt
            )
        }
    }
}
