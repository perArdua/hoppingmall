package com.hoppingmall.settlement.domain.repository

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.enums.SettlementStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Limit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.time.LocalDate

interface SettlementRepository : JpaRepository<Settlement, Long> {
    fun existsBySellerIdAndPeriodStartAndPeriodEnd(sellerId: Long, periodStart: LocalDate, periodEnd: LocalDate): Boolean
    fun existsBySellerIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(sellerId: Long, periodEnd: LocalDate, periodStart: LocalDate): Boolean
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<Settlement>
    fun findBySellerIdAndStatus(sellerId: Long, status: SettlementStatus, pageable: Pageable): Page<Settlement>
    fun findByStatus(status: SettlementStatus, pageable: Pageable): Page<Settlement>

    @Lock(LockModeType.PESSIMISTIC_READ)
    fun findWithShareLockByIdGreaterThanOrderByIdAsc(id: Long, limit: Limit): List<Settlement>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): Settlement?
}
