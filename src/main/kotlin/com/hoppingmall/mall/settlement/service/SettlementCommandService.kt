package com.hoppingmall.mall.settlement.service

import com.hoppingmall.mall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.mall.settlement.dto.response.SettlementResponse

interface SettlementCommandService {
    fun createSettlement(request: CreateSettlementRequest): SettlementResponse
    fun confirmSettlement(settlementId: Long): SettlementResponse
    fun paySettlement(settlementId: Long): SettlementResponse
}
