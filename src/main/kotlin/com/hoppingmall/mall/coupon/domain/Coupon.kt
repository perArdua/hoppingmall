package com.hoppingmall.mall.coupon.domain

import com.hoppingmall.mall.coupon.enum.CouponStatus
import com.hoppingmall.mall.coupon.enum.DiscountType
import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Filter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Entity
@Table(name = "coupons")
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class Coupon private constructor(
    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val code: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val discountType: DiscountType,

    @Column(nullable = false, precision = 10, scale = 2)
    val discountValue: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 2)
    val minOrderAmount: BigDecimal,

    @Column(precision = 10, scale = 2)
    val maxDiscountAmount: BigDecimal?,

    @Column(nullable = false)
    val totalQuantity: Int,

    @Column(nullable = false)
    var issuedQuantity: Int = 0,

    @Column(nullable = false)
    val validFrom: LocalDateTime,

    @Column(nullable = false)
    val validTo: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CouponStatus = CouponStatus.ACTIVE
) : BaseEntity() {

    companion object {
        fun create(
            name: String,
            code: String,
            discountType: DiscountType,
            discountValue: BigDecimal,
            minOrderAmount: BigDecimal,
            maxDiscountAmount: BigDecimal?,
            totalQuantity: Int,
            validFrom: LocalDateTime,
            validTo: LocalDateTime
        ): Coupon {
            return Coupon(
                name = name,
                code = code,
                discountType = discountType,
                discountValue = discountValue,
                minOrderAmount = minOrderAmount,
                maxDiscountAmount = maxDiscountAmount,
                totalQuantity = totalQuantity,
                validFrom = validFrom,
                validTo = validTo
            )
        }
    }

    fun calculateDiscount(orderAmount: BigDecimal): BigDecimal {
        val discount = when (discountType) {
            DiscountType.FIXED_AMOUNT -> discountValue
            DiscountType.PERCENTAGE -> {
                val calculated = orderAmount.multiply(discountValue)
                    .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
                val max = maxDiscountAmount
                if (max != null && calculated > max) max else calculated
            }
        }
        return if (discount > orderAmount) orderAmount else discount
    }

    fun isValid(): Boolean {
        val now = LocalDateTime.now()
        return status == CouponStatus.ACTIVE &&
            now.isAfter(validFrom) &&
            now.isBefore(validTo)
    }

    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(validTo)
    }

    fun isExhausted(): Boolean {
        return issuedQuantity >= totalQuantity
    }

    fun issue() {
        issuedQuantity++
    }

    fun changeStatus(status: CouponStatus) {
        this.status = status
    }
}
