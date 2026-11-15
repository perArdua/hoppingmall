package com.hoppingmall.product.product.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.EnumNameConverter
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.common.enums.WithdrawalRestrictionReason
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_seller_id", columnList = "sellerId"),
        Index(name = "idx_products_category_id", columnList = "categoryId")
    ]
)
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

    @Convert(converter = ProductStatusConverter::class)
    @Column(nullable = false, length = 32)
    var status: ProductStatus = ProductStatus.AVAILABLE

): BaseEntity() {

    @ColumnDefault("0")
    @Column(nullable = false, insertable = false, updatable = false)
    var reviewCount: Long = 0

    @ColumnDefault("0.00")
    @Column(nullable = false, precision = 3, scale = 2, insertable = false, updatable = false)
    var averageRating: BigDecimal = BigDecimal.ZERO

    @ColumnDefault("false")
    @Column(nullable = false)
    var withdrawalRestricted: Boolean = false
        private set

    @Convert(converter = WithdrawalRestrictionReasonConverter::class)
    @Column(length = 32)
    var withdrawalRestrictionReason: WithdrawalRestrictionReason? = null
        private set

    fun changeWithdrawalRestriction(restricted: Boolean, reason: WithdrawalRestrictionReason?) {
        require(restricted == (reason != null)) { "청약철회를 제한하면 사유가 있어야 하고, 제한하지 않으면 사유가 없어야 합니다" }
        this.withdrawalRestricted = restricted
        this.withdrawalRestrictionReason = reason
    }

    fun update(name: String, description: String, price: BigDecimal, categoryId: Long, status: ProductStatus?) {
        require(price > BigDecimal.ZERO) { "가격은 0보다 커야 합니다" }
        this.name = name
        this.description = description
        this.price = price
        this.categoryId = categoryId
        status?.let { this.status = it }
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
            require(price > BigDecimal.ZERO) { "가격은 0보다 커야 합니다" }
            return Product(sellerId, categoryId, name, description, price, status)
        }
    }
}

class ProductStatusConverter : EnumNameConverter<ProductStatus>(ProductStatus::class.java)

class WithdrawalRestrictionReasonConverter : EnumNameConverter<WithdrawalRestrictionReason>(WithdrawalRestrictionReason::class.java)
