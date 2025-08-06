package com.hoppingmall.mall.point.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import com.hoppingmall.mall.point.enum.PointType
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "point_histories")
class PointHistory(
    @Column(nullable = false)
    val userId: Long,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: PointType,
    
    @Column
    val reason: String? = null,
    
    @Column
    val orderId: Long? = null,
    
    @Column
    val paymentId: Long? = null
) : BaseEntity() {

    companion object {
        // Fixture를 위한 빈 companion object
    }
} 