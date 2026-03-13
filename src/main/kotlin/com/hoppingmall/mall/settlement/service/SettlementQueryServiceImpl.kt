package com.hoppingmall.mall.settlement.service

import com.hoppingmall.mall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.mall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.mall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.mall.settlement.dto.response.SettlementResponse
import com.hoppingmall.mall.settlement.enum.SettlementStatus
import com.hoppingmall.mall.settlement.exception.SettlementAccessDeniedException
import com.hoppingmall.mall.settlement.exception.SettlementNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SettlementQueryServiceImpl(
    private val settlementRepository: SettlementRepository,
    private val settlementItemRepository: SettlementItemRepository
) : SettlementQueryService {

    override fun getSettlements(
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

    override fun getSettlementDetail(settlementId: Long, sellerId: Long?): SettlementDetailResponse {
        val settlement = settlementRepository.findById(settlementId)
            .orElseThrow { SettlementNotFoundException() }

        if (sellerId != null && settlement.sellerId != sellerId) {
            throw SettlementAccessDeniedException()
        }

        val items = settlementItemRepository.findBySettlementId(settlementId)
        return SettlementDetailResponse.from(settlement, items)
    }
}
