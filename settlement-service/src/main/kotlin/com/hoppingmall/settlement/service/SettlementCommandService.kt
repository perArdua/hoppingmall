package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementResponse

interface SettlementCommandService {
    fun createSettlement(request: CreateSettlementRequest): SettlementResponse
    fun confirmSettlement(settlementId: Long): SettlementResponse
    fun paySettlement(settlementId: Long): SettlementResponse
}
