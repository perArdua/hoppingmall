package com.hoppingmall.mall.product.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "product_images")
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class ProductImage private constructor(

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var imageUrl: String,

): BaseEntity() {

    companion object {
        fun create(
            productId: Long,
            imageUrl: String,
        ): ProductImage {
            return ProductImage(productId, imageUrl)
        }
    }
}  