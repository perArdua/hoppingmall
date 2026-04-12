package com.hoppingmall.product.product.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
@Entity
@Table(
    name = "product_images",
    indexes = [Index(name = "idx_product_images_product_id", columnList = "productId")]
)
class ProductImage private constructor(

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var imageUrl: String,

    @Column(nullable = false)
    val sortOrder: Int = 0

) : BaseEntity() {

    companion object {
        fun create(
            productId: Long,
            imageUrl: String,
            sortOrder: Int = 0
        ): ProductImage = ProductImage(productId, imageUrl, sortOrder)
    }
}
