package com.hoppingmall.order.refund.domain

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.refund.config.RefundPolicyProperties
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.enum.ShippingStatus
import java.time.LocalDate
import java.time.LocalDateTime

class RefundDeadline(private val policy: RefundPolicyProperties) {

    fun lastDay(reason: RefundReason, receivedAt: LocalDateTime): LocalDate {
        val receivedOn = receivedAt.toLocalDate()
        return if (reason.isSellerFault) {
            receivedOn.plusMonths(policy.sellerFaultWindowMonths)
        } else {
            receivedOn.plusDays(policy.changeOfMindWindowDays)
        }
    }

    fun isClosed(reason: RefundReason, receivedAt: LocalDateTime, now: LocalDateTime): Boolean =
        now.toLocalDate().isAfter(lastDay(reason, receivedAt))

    fun allWindowsClosedBefore(today: LocalDate, retentionDays: Long): LocalDateTime {
        val base = today.minusDays(retentionDays)
        return minOf(
            base.minusDays(policy.changeOfMindWindowDays),
            base.minusMonths(policy.sellerFaultWindowMonths)
        ).atStartOfDay()
    }

    companion object {
        fun receivedAt(order: Order, shipping: Shipping?): LocalDateTime? =
            order.deliveredAt
                ?: shipping?.takeIf { it.status == ShippingStatus.DELIVERED }?.updatedAt
                ?: order.updatedAt?.takeIf { order.status == OrderStatus.DELIVERED }
    }
}
