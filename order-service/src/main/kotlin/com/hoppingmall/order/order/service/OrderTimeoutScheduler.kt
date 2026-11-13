package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.springframework.transaction.support.TransactionTemplate

class OrderTimeoutScheduler(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderCommandService: OrderCommandService,
    private val paymentQueryPort: PaymentQueryPort,
    private val inventoryCommandPort: InventoryCommandPort,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    fun checkTimedOutOrders() {
    }
}
