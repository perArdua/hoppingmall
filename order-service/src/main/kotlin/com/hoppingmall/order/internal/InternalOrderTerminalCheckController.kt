package com.hoppingmall.order.internal

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/internal/api/v1")
class InternalOrderTerminalCheckController(
    private val orderRepository: OrderRepository,
    @Value("\${order.terminal.retention-days:30}") private val retentionDays: Long
) {

    @PostMapping("/orders/terminal-check")
    fun terminalCheck(@Valid @RequestBody request: TerminalCheckRequest): ResponseEntity<ApiResponse<TerminalCheckResponse>> {
        val orderIds = request.orderIds.orEmpty().distinct()
        val terminalOrderIds = if (orderIds.isEmpty()) {
            emptyList()
        } else {
            orderRepository.findTerminalOrderIds(
                orderIds,
                Order.TERMINAL_STATUSES,
                LocalDateTime.now().minusDays(retentionDays)
            ).sorted()
        }
        return ResponseEntity.ok(ApiResponse.success(TerminalCheckResponse(terminalOrderIds)))
    }

    data class TerminalCheckRequest(
        @field:NotNull(message = "order_ids는 필수입니다")
        @field:Size(max = MAX_ORDER_IDS, message = "order_ids는 최대 500개입니다")
        val orderIds: List<Long>?
    )

    data class TerminalCheckResponse(
        val terminalOrderIds: List<Long>
    )

    companion object {
        const val MAX_ORDER_IDS = 500
    }
}
