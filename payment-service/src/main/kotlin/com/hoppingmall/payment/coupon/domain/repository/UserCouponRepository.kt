package com.hoppingmall.payment.coupon.domain.repository

import com.hoppingmall.payment.coupon.domain.UserCoupon
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserCouponRepository : JpaRepository<UserCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCoupon?

    fun findByUserId(userId: Long, pageable: Pageable): Slice<UserCoupon>

    fun findByOrderId(orderId: Long): UserCoupon?

    @Query("SELECT uc.userId FROM UserCoupon uc WHERE uc.couponId = :couponId")
    fun findUserIdsByCouponId(@Param("couponId") couponId: Long): List<Long>
}
