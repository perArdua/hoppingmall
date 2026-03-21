package com.hoppingmall.payment.point.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "points")
class Point(
    @Column(nullable = false, unique = true)
    val userId: Long,

    @Column(nullable = false, precision = 10, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO
) : BaseEntity() {

    companion object {
    }

    fun addPoints(points: BigDecimal) {
        if (points < BigDecimal.ZERO) {
            throw IllegalArgumentException("포인트는 음수일 수 없습니다")
        }
        this.balance = this.balance.add(points)
    }

    fun usePoints(points: BigDecimal) {
        if (points < BigDecimal.ZERO) {
            throw IllegalArgumentException("포인트는 음수일 수 없습니다")
        }
        if (this.balance < points) {
            throw IllegalArgumentException("포인트 잔액이 부족합니다")
        }
        this.balance = this.balance.subtract(points)
    }
}
