package com.hoppingmall.mall.settlement.domain.repository

import com.hoppingmall.mall.settlement.domain.Settlement
import com.hoppingmall.mall.settlement.enum.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface SettlementRepository : JpaRepository<Settlement, Long> {
    fun existsBySellerIdAndPeriodStartAndPeriodEnd(sellerId: Long, periodStart: LocalDate, periodEnd: LocalDate): Boolean
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<Settlement>
    fun findBySellerIdAndStatus(sellerId: Long, status: SettlementStatus, pageable: Pageable): Page<Settlement>
    fun findByStatus(status: SettlementStatus, pageable: Pageable): Page<Settlement>
}
