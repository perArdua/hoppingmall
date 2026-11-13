package com.hoppingmall.payment.support

import com.hoppingmall.payment.port.OrderSummary
import com.hoppingmall.payment.port.OrderSummaryQueryPort
import com.hoppingmall.payment.port.exception.OrderSummaryQueryFailedException
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeOrderSummaryQueryPort : OrderSummaryQueryPort {

    private val orders = ConcurrentHashMap<Long, OrderSummary>()

    val requestedOrderIds = CopyOnWriteArrayList<Long>()

    @Volatile
    var unavailable = false

    fun put(orderId: Long, buyerId: Long, totalAmount: BigDecimal, status: String = "PAYING") {
        orders[orderId] = OrderSummary(orderId = orderId, buyerId = buyerId, totalAmount = totalAmount, status = status)
    }

    override fun findOrder(orderId: Long): OrderSummary? {
        requestedOrderIds.add(orderId)
        if (unavailable) {
            throw OrderSummaryQueryFailedException(orderId, IllegalStateException("order-service unavailable"))
        }
        return orders[orderId]
    }
}
