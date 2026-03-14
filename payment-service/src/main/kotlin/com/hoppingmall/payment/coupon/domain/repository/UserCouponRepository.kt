package com.hoppingmall.payment.coupon.domain.repository

import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserCouponRepository : JpaRepository<UserCoupon, Long> {

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCoupon?

    fun findByUserIdAndStatus(userId: Long, status: UserCouponStatus): List<UserCoupon>

    fun findByUserId(userId: Long, pageable: Pageable): Slice<UserCoupon>

    fun findByOrderId(orderId: Long): UserCoupon?
}
