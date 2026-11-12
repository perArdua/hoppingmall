package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.exception.InventoryBudgetExceededException

object ReserveDeadline {
    const val HEADER = "X-Reserve-Budget-Ms"
    const val MARGIN_MS = 200L
    const val EXPIRY_GRACE_MS = 10_000L

    private val deadline = ThreadLocal<Long?>()

    fun set(deadlineMs: Long) = deadline.set(deadlineMs)

    fun clear() = deadline.remove()

    fun current(): Long? = deadline.get()

    fun check(now: Long = System.currentTimeMillis()) {
        val d = deadline.get() ?: return
        if (now > d - MARGIN_MS) throw InventoryBudgetExceededException()
    }
}
