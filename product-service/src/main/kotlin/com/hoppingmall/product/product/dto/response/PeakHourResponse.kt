package com.hoppingmall.product.product.dto.response

import com.hoppingmall.product.product.dto.HourlyAggregationProjection
import java.math.BigDecimal

data class PeakHourResponse(
    val hour: Int,
    val totalSalesAmount: BigDecimal,
    val totalOrderCount: Long
) {
    companion object {
        fun from(projection: HourlyAggregationProjection): PeakHourResponse {
            return PeakHourResponse(
                hour = projection.getHour(),
                totalSalesAmount = projection.getTotalAmount(),
                totalOrderCount = projection.getTotalOrders()
            )
        }
    }
}
