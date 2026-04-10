package com.hoppingmall.settlement.domain.repository

import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.enums.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SettlementSummaryRepository : JpaRepository<SettlementSummary, Long> {
    fun findBySellerIdOrderByPeriodStartDesc(sellerId: Long, pageable: Pageable): Page<SettlementSummary>
    fun findBySellerIdAndPeriodStartAndPeriodEnd(sellerId: Long, periodStart: LocalDate, periodEnd: LocalDate): SettlementSummary?
    fun findBySettlementId(settlementId: Long): SettlementSummary?
    fun findBySettlementIdIn(settlementIds: List<Long>): List<SettlementSummary>
    fun findBySellerIdAndStatus(sellerId: Long, status: SettlementStatus, pageable: Pageable): Page<SettlementSummary>
    fun findByStatus(status: SettlementStatus, pageable: Pageable): Page<SettlementSummary>
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<SettlementSummary>
}
