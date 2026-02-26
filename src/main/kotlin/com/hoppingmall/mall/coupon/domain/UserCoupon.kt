package com.hoppingmall.mall.coupon.domain

import com.hoppingmall.mall.coupon.enum.UserCouponStatus
import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Filter
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "coupon_id"])]
)
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class UserCoupon private constructor(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val couponId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserCouponStatus = UserCouponStatus.ISSUED,

    @Column
    var usedAt: LocalDateTime? = null,

    @Column
    var orderId: Long? = null
) : BaseEntity() {

    companion object {
        fun create(
            userId: Long,
            couponId: Long
        ): UserCoupon {
            return UserCoupon(
                userId = userId,
                couponId = couponId
            )
        }
    }

    fun use(orderId: Long) {
        this.status = UserCouponStatus.USED
        this.usedAt = LocalDateTime.now()
        this.orderId = orderId
    }

    fun restore() {
        this.status = UserCouponStatus.ISSUED
        this.usedAt = null
        this.orderId = null
    }
}
