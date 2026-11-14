package com.hoppingmall.settlement.domain.repository

import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.enums.SettlementStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementSummaryRepository : JpaRepository<SettlementSummary, Long> {
    fun existsByIdIsNotNull(): Boolean
    fun findBySettlementId(settlementId: Long): SettlementSummary?
    fun findBySettlementIdIn(settlementIds: List<Long>): List<SettlementSummary>
    fun findBySellerIdAndStatus(sellerId: Long, status: SettlementStatus, pageable: Pageable): Page<SettlementSummary>
    fun findByStatus(status: SettlementStatus, pageable: Pageable): Page<SettlementSummary>
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<SettlementSummary>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockBySettlementId(settlementId: Long): SettlementSummary?

    @Modifying
    @Query(
        value = """
            INSERT INTO settlement_summaries (settlement_id, seller_id, period_start, period_end, total_sales_amount,
              total_refund_amount, commission_rate, commission_amount, settlement_amount, status, confirmed_at, paid_at,
              created_at, updated_at)
            VALUES (:#{#summary.settlementId}, :#{#summary.sellerId}, :#{#summary.periodStart}, :#{#summary.periodEnd},
              :#{#summary.totalSalesAmount}, :#{#summary.totalRefundAmount}, :#{#summary.commissionRate},
              :#{#summary.commissionAmount}, :#{#summary.settlementAmount}, :#{#summary.status.name()},
              :#{#summary.confirmedAt}, :#{#summary.paidAt}, :#{#summary.createdAt}, :#{#summary.createdAt})
            ON DUPLICATE KEY UPDATE settlement_id = settlement_id
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(@Param("summary") summary: SettlementSummary): Int
}
