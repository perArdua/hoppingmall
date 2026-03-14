package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.refund.domain.Refund
import com.hoppingmall.mall.refund.enum.RefundReason
import com.hoppingmall.mall.refund.enum.RefundStatus
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun Refund.Companion.fixture(
    orderId: Long = 1L,
    paymentId: Long = 1L,
    buyerId: Long = 1L,
    sellerId: Long = 2L,
    reason: RefundReason = RefundReason.CHANGE_OF_MIND,
    reasonDetail: String? = null,
    refundAmount: BigDecimal = BigDecimal("30000"),
    isFullRefund: Boolean = true,
    status: RefundStatus = RefundStatus.REQUESTED
): Refund {
    return Refund.create(
        orderId = orderId,
        paymentId = paymentId,
        buyerId = buyerId,
        sellerId = sellerId,
        reason = reason,
        reasonDetail = reasonDetail,
        refundAmount = refundAmount,
        isFullRefund = isFullRefund
    ).apply {
        this.status = status
    }.withId(1L)
}

fun Refund.Companion.approvedFixture(
    orderId: Long = 1L,
    paymentId: Long = 1L,
    buyerId: Long = 1L,
    sellerId: Long = 2L,
    refundAmount: BigDecimal = BigDecimal("30000"),
    isFullRefund: Boolean = true
): Refund {
    return Refund.fixture(
        orderId = orderId,
        paymentId = paymentId,
        buyerId = buyerId,
        sellerId = sellerId,
        refundAmount = refundAmount,
        isFullRefund = isFullRefund,
        status = RefundStatus.APPROVED
    )
}
