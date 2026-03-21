package com.hoppingmall.product.product.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.product.common.enums.ProductStatus
import jakarta.persistence.*
import org.hibernate.annotations.Filter
import java.math.BigDecimal

@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_seller_id", columnList = "sellerId"),
        Index(name = "idx_products_category_id", columnList = "categoryId")
    ]
)
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class Product private constructor(

    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    var categoryId: Long,

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

    fun update(name: String, description: String, price: BigDecimal, categoryId: Long, status: ProductStatus) {
        require(price > BigDecimal.ZERO) { "가격은 0보다 커야 합니다" }
        this.name = name
        this.description = description
        this.price = price
        this.categoryId = categoryId
        this.status = status
    }

    companion object {
        fun create(
            sellerId: Long,
            categoryId: Long,
            name: String,
            description: String,
            price: BigDecimal,
            status: ProductStatus
        ): Product {
            return Product(sellerId, categoryId, name, description, price, status)
        }
    }
}