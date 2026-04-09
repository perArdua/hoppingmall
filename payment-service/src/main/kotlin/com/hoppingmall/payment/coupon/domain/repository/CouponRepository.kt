package com.hoppingmall.payment.coupon.domain.repository

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.enum.CouponStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CouponRepository : JpaRepository<Coupon, Long> {

    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    fun findActiveById(@Param("id") id: Long): Coupon?

    @Query(
        "SELECT c FROM Coupon c WHERE c.status = :status AND c.validFrom <= :now AND c.validTo > :now " +
            "AND c.issuedQuantity < c.totalQuantity"
    )
    fun findAvailableCoupons(
        @Param("status") status: CouponStatus = CouponStatus.ACTIVE,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<Coupon>

    @Query("SELECT c FROM Coupon c")
    fun findAllActive(): List<Coupon>
}
