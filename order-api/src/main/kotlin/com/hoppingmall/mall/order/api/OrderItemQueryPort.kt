package com.hoppingmall.mall.order.api

import java.time.LocalDateTime

interface OrderItemQueryPort {
    fun findDeliveredItemsBySellerAndPeriod(
        sellerId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<OrderItemInfo>
}
