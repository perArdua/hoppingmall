package com.hoppingmall.mall.coupon.domain.repository

import com.hoppingmall.mall.coupon.domain.Coupon
import com.hoppingmall.mall.coupon.enum.CouponStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CouponRepository : JpaRepository<Coupon, Long> {

    @Query("SELECT c FROM Coupon c WHERE c.id = :id AND c.deletedAt IS NULL")
    fun findActiveById(@Param("id") id: Long): Coupon?

    @Query(
        "SELECT c FROM Coupon c WHERE c.status = :status AND c.validFrom <= :now AND c.validTo > :now " +
            "AND c.issuedQuantity < c.totalQuantity AND c.deletedAt IS NULL"
    )
    fun findAvailableCoupons(
        @Param("status") status: CouponStatus = CouponStatus.ACTIVE,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<Coupon>

    @Query("SELECT c FROM Coupon c WHERE c.deletedAt IS NULL")
    fun findAllActive(): List<Coupon>
}
