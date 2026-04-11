package com.hoppingmall.settlement.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import com.hoppingmall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.enums.SettlementStatus
import com.hoppingmall.settlement.exception.SettlementAccessDeniedException
import com.hoppingmall.settlement.exception.SettlementNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SettlementQueryServiceImpl(
    private val settlementRepository: SettlementRepository,
    private val settlementItemRepository: SettlementItemRepository,
    private val settlementSummaryRepository: SettlementSummaryRepository
) : SettlementQueryService {

    override fun getSettlements(
        sellerId: Long?,
        status: SettlementStatus?,
        pageable: Pageable
    ): Page<SettlementResponse> {
        val summaries = findSummaries(sellerId, status, pageable)
        if (summaries.totalElements > 0) {
            return summaries.map { it.toResponse() }
        }
        return findFromWriteModel(sellerId, status, pageable)
    }

    override fun getSettlementDetail(settlementId: Long, sellerId: Long?): SettlementDetailResponse {
        val settlement = settlementRepository.findByIdOrNull(settlementId) ?: throw SettlementNotFoundException() 

        if (sellerId != null && settlement.sellerId != sellerId) {
            throw SettlementAccessDeniedException()
        }

        val items = settlementItemRepository.findBySettlementId(settlementId)
        return SettlementDetailResponse.from(settlement, items)
    }

    private fun findSummaries(
        sellerId: Long?,
        status: SettlementStatus?,
        pageable: Pageable
    ): Page<SettlementSummary> {
        return when {
            sellerId != null && status != null ->
                settlementSummaryRepository.findBySellerIdAndStatus(sellerId, status, pageable)
            sellerId != null ->
                settlementSummaryRepository.findBySellerId(sellerId, pageable)
            status != null ->
                settlementSummaryRepository.findByStatus(status, pageable)
            else ->
                settlementSummaryRepository.findAll(pageable)
        }
    }

    private fun findFromWriteModel(
        sellerId: Long?,
        status: SettlementStatus?,
        pageable: Pageable
    ): Page<SettlementResponse> {
        val settlements = when {
            sellerId != null && status != null ->
                settlementRepository.findBySellerIdAndStatus(sellerId, status, pageable)
            sellerId != null ->
                settlementRepository.findBySellerId(sellerId, pageable)
            status != null ->
                settlementRepository.findByStatus(status, pageable)
            else ->
                settlementRepository.findAll(pageable)
        }
        return settlements.map { SettlementResponse.from(it) }
    }

    private fun SettlementSummary.toResponse(): SettlementResponse {
        return SettlementResponse(
            id = this.settlementId,
            sellerId = this.sellerId,
            periodStart = this.periodStart,
            periodEnd = this.periodEnd,
            totalSalesAmount = this.totalSalesAmount,
            totalRefundAmount = this.totalRefundAmount,
            commissionRate = this.commissionRate,
            commissionAmount = this.commissionAmount,
            settlementAmount = this.settlementAmount,
            status = this.status,
            confirmedAt = this.confirmedAt,
            paidAt = this.paidAt,
            createdAt = this.createdAt
        )
    }
}
