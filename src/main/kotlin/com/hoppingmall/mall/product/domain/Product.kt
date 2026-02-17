package com.hoppingmall.mall.product.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import com.hoppingmall.mall.global.enums.ProductStatus
import jakarta.persistence.*
import org.hibernate.annotations.Filter
import java.math.BigDecimal

@Entity
@Table(name = "products")
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class Product private constructor(

    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String,

    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = ProductStatus.AVAILABLE

): BaseEntity() {

    companion object {
        fun create(
            sellerId: Long,
            name: String,
            description: String,
            price: BigDecimal,
            status: ProductStatus
        ): Product{
            return Product(sellerId, name, description, price, status)
        }
    }
}