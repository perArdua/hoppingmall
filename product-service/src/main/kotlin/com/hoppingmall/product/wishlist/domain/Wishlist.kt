package com.hoppingmall.product.wishlist.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "wishlists",
    uniqueConstraints = [UniqueConstraint(columnNames = ["buyer_id", "product_id"])]
)
class Wishlist private constructor(
    @Column
    val buyerId: Long,

    @Column
    val productId: Long,
) : BaseEntity() {
    companion object {
        fun create(buyerId: Long, productId: Long): Wishlist {
            return Wishlist(buyerId, productId)
        }
    }
}
