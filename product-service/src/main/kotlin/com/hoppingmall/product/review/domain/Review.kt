package com.hoppingmall.product.review.domain

import com.hoppingmall.product.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Filter

@Entity
@Table(
    name = "reviews",
    uniqueConstraints = [UniqueConstraint(columnNames = ["order_item_id"])],
    indexes = [
        Index(name = "idx_reviews_product_id", columnList = "productId"),
        Index(name = "idx_reviews_buyer_id", columnList = "buyerId")
    ]
)
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class Review private constructor(
    @Column(nullable = false)
    val buyerId: Long,

    @Column(nullable = false)
    val orderItemId: Long,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var rating: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column
    var imageUrl: String? = null
) : BaseEntity() {

    companion object {
        fun create(
            buyerId: Long,
            orderItemId: Long,
            productId: Long,
            rating: Int,
            content: String,
            imageUrl: String? = null
        ): Review {
            return Review(
                buyerId = buyerId,
                orderItemId = orderItemId,
                productId = productId,
                rating = rating,
                content = content,
                imageUrl = imageUrl
            )
        }
    }

    fun update(rating: Int, content: String, imageUrl: String?) {
        this.rating = rating
        this.content = content
        this.imageUrl = imageUrl
    }
}
