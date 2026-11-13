package com.hoppingmall.order.refund.domain

import com.hoppingmall.order.order.domain.OrderItem
import java.math.BigDecimal
import java.math.RoundingMode

class RefundAmountCalculator(
    orderItems: List<OrderItem>,
    discountAmount: BigDecimal
) {
    private val orderItemMap = orderItems.associateBy { it.id!! }
    private val paidLineAmounts = allocatePaidLineAmounts(orderItems, discountAmount)

    fun refundPrice(
        orderItemId: Long,
        quantity: Int,
        alreadyRefundedQuantity: Int,
        alreadyRefundedAmount: BigDecimal
    ): BigDecimal {
        val orderItem = orderItemMap.getValue(orderItemId)
        val paidLineAmount = paidLineAmounts.getValue(orderItemId)
        if (alreadyRefundedQuantity + quantity >= orderItem.quantity) {
            return paidLineAmount.subtract(alreadyRefundedAmount).max(BigDecimal.ZERO)
        }
        return paidLineAmount.multiply(BigDecimal(quantity))
            .divide(BigDecimal(orderItem.quantity), SCALE, RoundingMode.DOWN)
    }

    companion object {
        private const val SCALE = 2

        private fun allocatePaidLineAmounts(orderItems: List<OrderItem>, discountAmount: BigDecimal): Map<Long, BigDecimal> {
            val itemsTotal = orderItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.totalPrice) }
            val discount = discountAmount.max(BigDecimal.ZERO).min(itemsTotal)
            if (discount.signum() == 0) {
                return orderItems.associate { it.id!! to it.totalPrice }
            }

            val remainderHolder = orderItems.maxWith(compareBy<OrderItem> { it.totalPrice }.thenByDescending { it.id!! })
            val shares = orderItems.filter { it !== remainderHolder }.associate { item ->
                item.id!! to discount.multiply(item.totalPrice).divide(itemsTotal, SCALE, RoundingMode.DOWN)
            }
            val remainderShare = discount.subtract(shares.values.fold(BigDecimal.ZERO, BigDecimal::add))

            return orderItems.associate { item ->
                item.id!! to item.totalPrice.subtract(shares[item.id!!] ?: remainderShare)
            }
        }
    }
}
