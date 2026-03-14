package com.hoppingmall.payment.point.domain

import com.hoppingmall.payment.common.BaseEntity
import com.hoppingmall.payment.point.enum.PointType
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "point_histories",
    indexes = [Index(name = "idx_point_histories_user_id", columnList = "userId")]
)
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
    val paymentId: Long? = null,

    @Column(unique = true)
    val eventId: String? = null
) : BaseEntity() {

    companion object {
    }
}
