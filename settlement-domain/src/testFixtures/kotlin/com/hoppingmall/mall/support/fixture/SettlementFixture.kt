package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.settlement.domain.Settlement
import com.hoppingmall.mall.settlement.domain.SettlementItem
import java.math.BigDecimal
import java.time.LocalDate

fun Settlement.Companion.fixture(
    sellerId: Long = 1L,
    periodStart: LocalDate = LocalDate.of(2026, 3, 1),
    periodEnd: LocalDate = LocalDate.of(2026, 3, 31),
    totalSalesAmount: BigDecimal = BigDecimal("1000000"),
    totalRefundAmount: BigDecimal = BigDecimal("50000"),
    commissionRate: BigDecimal = BigDecimal("0.1000"),
    commissionAmount: BigDecimal = BigDecimal("100000"),
    settlementAmount: BigDecimal = BigDecimal("850000")
): Settlement {
    return Settlement.create(
        sellerId = sellerId,
        periodStart = periodStart,
        periodEnd = periodEnd,
        totalSalesAmount = totalSalesAmount,
        totalRefundAmount = totalRefundAmount,
        commissionRate = commissionRate,
        commissionAmount = commissionAmount,
        settlementAmount = settlementAmount
    )
}

fun SettlementItem.Companion.fixture(
    settlementId: Long = 1L,
    orderId: Long = 1L,
    orderItemId: Long = 1L,
    productName: String = "테스트 상품",
    quantity: Int = 2,
    salesAmount: BigDecimal = BigDecimal("50000")
): SettlementItem {
    return SettlementItem.create(
        settlementId = settlementId,
        orderId = orderId,
        orderItemId = orderItemId,
        productName = productName,
        quantity = quantity,
        salesAmount = salesAmount
    )
}
