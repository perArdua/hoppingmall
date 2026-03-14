package com.hoppingmall.payment.coupon.domain

import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import com.hoppingmall.payment.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Filter
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "coupon_id"])],
    indexes = [Index(name = "idx_user_coupons_user_id", columnList = "userId")]
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
