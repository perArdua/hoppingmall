package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.enums.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface SettlementQueryService {
    fun getSettlements(sellerId: Long?, status: SettlementStatus?, pageable: Pageable): Page<SettlementResponse>
    fun getSettlementDetail(settlementId: Long, sellerId: Long?): SettlementDetailResponse
}
