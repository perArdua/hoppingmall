package com.hoppingmall.payment.point.domain

import com.hoppingmall.payment.point.enum.PointType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryRepository : JpaRepository<PointHistory, Long> {
    fun existsByEventId(eventId: String): Boolean

    fun findByUserId(userId: Long, pageable: Pageable): Slice<PointHistory>

    fun findByPaymentIdAndType(paymentId: Long, type: PointType): PointHistory?

    fun findAllByPaymentIdAndType(paymentId: Long, type: PointType): List<PointHistory>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM PointHistory h WHERE h.userId = :userId AND h.paymentId = :paymentId")
    fun findAllByUserIdAndPaymentIdForUpdate(@Param("userId") userId: Long, @Param("paymentId") paymentId: Long): List<PointHistory>
}
