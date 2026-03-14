package com.hoppingmall.payment.point.domain

import com.hoppingmall.payment.point.enum.PointType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryRepository : JpaRepository<PointHistory, Long> {
    fun existsByEventId(eventId: String): Boolean

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>

    fun findByUserId(userId: Long, pageable: Pageable): Page<PointHistory>

    fun findByPaymentIdAndType(paymentId: Long, type: PointType): PointHistory?
}
