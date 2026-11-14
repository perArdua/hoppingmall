package com.hoppingmall.order.internal

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.order.order.domain.repository.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity

class InternalOrderTerminalCheckController(
    private val orderRepository: OrderRepository,
    @Value("\${order.terminal.retention-days:30}") private val retentionDays: Long
) {

    fun terminalCheck(request: TerminalCheckRequest): ResponseEntity<ApiResponse<TerminalCheckResponse>> =
        ResponseEntity.ok(ApiResponse.success(TerminalCheckResponse(emptyList())))

    data class TerminalCheckRequest(
        val orderIds: List<Long>?
    )

    data class TerminalCheckResponse(
        val terminalOrderIds: List<Long>
    )
}
