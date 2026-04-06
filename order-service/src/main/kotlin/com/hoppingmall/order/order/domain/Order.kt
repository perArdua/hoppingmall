package com.hoppingmall.order.order.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import jakarta.persistence.*
import org.hibernate.annotations.Filter
import java.math.BigDecimal

@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "idx_orders_buyer_id", columnList = "buyerId")]
)
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class Order private constructor(
    @Column(nullable = false)
    val buyerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.CREATED,

    @Column(nullable = false, precision = 10, scale = 2)
    val totalAmount: BigDecimal
) : BaseEntity() {

    companion object {
        private val allowedTransitions: Map<OrderStatus, Set<OrderStatus>> = mapOf(
            OrderStatus.CREATED to setOf(OrderStatus.PAYING, OrderStatus.CANCELLED),
            OrderStatus.PAYING to setOf(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID to setOf(OrderStatus.SHIPPED, OrderStatus.CANCEL_REQUESTED),
            OrderStatus.CANCEL_REQUESTED to setOf(OrderStatus.CANCELLED, OrderStatus.CANCEL_FAILED),
            OrderStatus.SHIPPED to setOf(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED to emptySet(),
            OrderStatus.CANCELLED to emptySet(),
            OrderStatus.CANCEL_FAILED to setOf(OrderStatus.CANCEL_REQUESTED)
        )

        fun create(
            buyerId: Long,
            totalAmount: BigDecimal
        ): Order {
            return Order(
                buyerId = buyerId,
                totalAmount = totalAmount
            )
        }
    }

    fun updateStatus(newStatus: OrderStatus) {
        val allowed = allowedTransitions[this.status] ?: emptySet()
        if (newStatus !in allowed) {
            throw OrderInvalidStatusException()
        }
        this.status = newStatus
    }

    fun isCancellable(): Boolean {
        val allowed = allowedTransitions[this.status] ?: emptySet()
        return OrderStatus.CANCELLED in allowed || OrderStatus.CANCEL_REQUESTED in allowed
    }

    fun isCancelled(): Boolean {
        return this.status == OrderStatus.CANCELLED
    }
}
