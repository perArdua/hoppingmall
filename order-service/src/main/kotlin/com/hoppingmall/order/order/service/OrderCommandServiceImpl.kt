package com.hoppingmall.order.order.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.response.OrderPriceChange
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderCartChangedException
import com.hoppingmall.order.order.exception.OrderEmptyItemsException
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.order.exception.OrderBusyException
import com.hoppingmall.order.order.exception.OrderOutOfStockException
import com.hoppingmall.order.order.exception.OrderPartiallyRefundedException
import com.hoppingmall.order.order.exception.OrderPriceChangedException
import com.hoppingmall.order.order.exception.OrderProductNotFoundException
import com.hoppingmall.order.order.exception.OrderProductUnavailableException
import com.hoppingmall.order.order.exception.OrderReservationFencedException
import com.hoppingmall.order.order.exception.OrderSelfPurchaseException
import com.hoppingmall.order.order.exception.OrderTotalAmountExceededException
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCancellationResult
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal

@Service
class OrderCommandServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val cartItemRepository: CartItemRepository,
    private val productQueryPort: ProductQueryPort,
    private val inventoryCommandPort: InventoryCommandPort,
    private val paymentCommandPort: PaymentCommandPort,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val orderMetrics: OrderMetrics,
    private val transactionTemplate: TransactionTemplate,
    private val refundRepository: RefundRepository
) : OrderCommandService {

    private val log = LoggerFactory.getLogger(javaClass)

    private val cancellationBlockingRefundStatuses = setOf(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.COMPLETED)

    @Autowired(required = false)
    var reservationFence: ReservationFence? = null

    @Value("\${order.timeout.paying-minutes:15}")
    private var payingTimeoutMinutes: Long = 15

    override fun createOrder(buyerId: Long, request: OrderCreateRequest): OrderResponse {
        val cartItems = cartItemRepository.findAllById(request.cartItemIds)

        if (cartItems.isEmpty() || cartItems.size != request.cartItemIds.distinct().size) {
            throw OrderEmptyItemsException()
        }

        val hasOtherBuyerItem = cartItems.any { it.buyerId != buyerId }
        if (hasOtherBuyerItem) {
            throw OrderAccessDeniedException()
        }

        val productIds = cartItems.map { it.productId }.distinct()
        val productMap = findProducts(productIds).associateBy { it.id }
        productIds.forEach { productId ->
            if (!productMap.containsKey(productId)) throw OrderProductNotFoundException()
        }
        if (productMap.values.any { it.sellerId == buyerId }) {
            throw OrderSelfPurchaseException()
        }

        val priceChanges = priceChanges(cartItems, productMap, request)
        if (priceChanges.isNotEmpty()) {
            throw OrderPriceChangedException(priceChanges)
        }

        val totalAmount = cartItems.fold(BigDecimal.ZERO) { acc, it -> acc.add(productMap.getValue(it.productId).price.multiply(BigDecimal(it.quantity))) }
        if (totalAmount > Order.MAX_TOTAL_AMOUNT) {
            throw OrderTotalAmountExceededException()
        }

        val items = cartItems.groupBy { it.productId }.map { (productId, rows) -> productId to rows.sumOf { it.quantity } }
        val reservationMap = reserveStock(items)

        val fence = reservationFence?.takeIf { it.enabled }
        val response = try {
            createOrderInTransaction(buyerId, request, cartItems, productMap, reservationMap, totalAmount, fence)
        } catch (e: Exception) {
            runCatching { inventoryCommandPort.cancelReservations(reservationMap.values.distinct()) }
                .onFailure { log.warn("주문 저장 실패 후 예약 취소 실패: rids={}, cause={}", reservationMap.values, e.javaClass.simpleName, it) }
            throw e
        }
        orderMetrics.recordOrderCreated(totalAmount)
        return response
    }

    private fun createOrderInTransaction(
        buyerId: Long,
        request: OrderCreateRequest,
        cartItems: List<CartItem>,
        productMap: Map<Long, ProductInfo>,
        reservationMap: Map<Long, String>,
        totalAmount: BigDecimal,
        fence: ReservationFence?
    ): OrderResponse =
        transactionTemplate.execute {
            if (fence != null && fence.link(reservationMap.values).isNotEmpty()) {
                throw OrderReservationFencedException()
            }
            val order = orderRepository.save(
                Order.create(
                    buyerId = buyerId,
                    totalAmount = totalAmount,
                    shippingAddress = request.shippingAddress?.toShippingAddress()
                )
            )

            val orderItems = cartItems.map { cartItem ->
                val product = productMap.getValue(cartItem.productId)
                OrderItem.create(
                    orderId = order.id!!,
                    sellerId = product.sellerId,
                    productId = cartItem.productId,
                    productName = product.name,
                    productPrice = product.price,
                    quantity = cartItem.quantity,
                    reservationId = reservationMap[cartItem.productId],
                    withdrawalRestricted = product.withdrawalRestricted,
                    withdrawalRestrictionReason = product.withdrawalRestrictionReason
                )
            }
            val savedOrderItems = orderItemRepository.saveAll(orderItems)
            order.updateStatus(OrderStatus.PAYING)

            claimCartItems(buyerId, cartItems)

            log.info("주문 생성: orderId={}, buyerId={}, totalAmount={}, itemCount={}", order.id, buyerId, totalAmount, orderItems.size)
            OrderResponse.from(order, savedOrderItems, payingTimeoutMinutes)
        }!!

    private fun claimCartItems(buyerId: Long, cartItems: List<CartItem>) {
        val claimed = cartItems.sortedBy { it.id }.all { cartItemRepository.deleteUnchanged(it.id!!, buyerId, it.quantity) == 1 }
        if (!claimed) {
            throw OrderCartChangedException()
        }
    }

    private fun priceChanges(
        cartItems: List<CartItem>,
        productMap: Map<Long, ProductInfo>,
        request: OrderCreateRequest
    ): List<OrderPriceChange> {
        val expectedPrices = request.expectedPrices.associate { it.cartItemId to it.productPrice }
        return cartItems.mapNotNull { cartItem ->
            val expectedPrice = expectedPrices[cartItem.id] ?: return@mapNotNull null
            val currentPrice = productMap.getValue(cartItem.productId).price
            if (expectedPrice.compareTo(currentPrice) == 0) return@mapNotNull null
            OrderPriceChange(
                cartItemId = cartItem.id!!,
                productId = cartItem.productId,
                expectedPrice = expectedPrice,
                currentPrice = currentPrice
            )
        }
    }

    private fun findProducts(productIds: List<Long>): List<ProductInfo> =
        try {
            productQueryPort.findProductsByIds(productIds)
        } catch (e: ProductQueryUnavailableException) {
            throw OrderProductUnavailableException()
        }

    private fun reserveStock(items: List<Pair<Long, Int>>): Map<Long, String> =
        try {
            inventoryCommandPort.batchReserveStock(items)
        } catch (e: HttpClientErrorException.BadRequest) {
            if (productErrorCode(e) == PRODUCT_INSUFFICIENT_STOCK) throw OrderOutOfStockException()
            throw e
        } catch (e: HttpClientErrorException.TooManyRequests) {
            throw OrderBusyException()
        }

    private fun productErrorCode(e: HttpClientErrorException): String? =
        runCatching { errorBodyReader.readTree(e.responseBodyAsString).path("code").textValue() }.getOrNull()

    override fun cancelOrder(buyerId: Long, orderId: Long): OrderResponse {
        val order = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException() 

        if (order.buyerId != buyerId) {
            throw OrderAccessDeniedException()
        }

        if (!order.isCancellable()) {
            throw OrderInvalidStatusException()
        }

        val orderItems = orderItemRepository.findByOrderId(orderId)

        when (order.status) {
            OrderStatus.PAYING -> {
                if (!cancelPaying(orderId, orderItems)) {
                    throw OrderInvalidStatusException()
                }
            }
            OrderStatus.CREATED -> {
                transactionTemplate.execute {
                    val target = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException() 
                    target.updateStatus(OrderStatus.CANCELLED)
                }
                restoreInventory(orderItems)
            }
            OrderStatus.PAID, OrderStatus.CANCEL_FAILED -> {
                val expectedStatus = order.status
                transactionTemplate.execute {
                    orderRepository.lockById(orderId)
                    val target = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException()
                    if (target.status != expectedStatus) {
                        throw OrderInvalidStatusException()
                    }
                    if (refundRepository.existsByOrderIdAndStatusInAndDeletedAtIsNull(orderId, cancellationBlockingRefundStatuses)) {
                        throw OrderPartiallyRefundedException()
                    }
                    target.requestCancellation()
                    orderRepository.cancelPreparingShipping(orderId)
                    transactionalEventPublisher.publishEvent(
                        aggregateType = "Order",
                        aggregateId = orderId.toString(),
                        eventType = "PaymentCancellationRequested",
                        eventData = mapOf(
                            "eventType" to "PaymentCancellationRequested",
                            "eventId" to "cancel-$orderId-${System.currentTimeMillis()}",
                            "orderId" to orderId
                        ),
                        topic = KafkaTopics.PAYMENT_COMPENSATION,
                        partitionKey = orderId.toString()
                    )
                }
            }
            else -> throw OrderInvalidStatusException()
        }

        val updatedOrder = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException() 
        orderMetrics.recordOrderCancelled()
        log.info("주문 취소: orderId={}, buyerId={}, status={}", orderId, buyerId, updatedOrder.status)
        return OrderResponse.from(updatedOrder, orderItems)
    }

    override fun cancelPayingOrder(orderId: Long): Boolean =
        cancelPaying(orderId, orderItemRepository.findByOrderId(orderId))

    private fun cancelPaying(orderId: Long, orderItems: List<OrderItem>): Boolean {
        val won = transactionTemplate.execute {
            orderRepository.lockById(orderId)
            val target = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException()
            if (target.status != OrderStatus.PAYING) {
                return@execute false
            }
            target.updateStatus(OrderStatus.CANCELLED)
            true
        } ?: false
        if (!won) {
            log.info("PAYING이 아니게 된 주문의 결제 전 취소 생략: orderId={}", orderId)
            return false
        }

        val result = paymentCommandPort.cancelPayment(orderId)
        if (result == PaymentCancellationResult.FAILED) {
            log.warn("결제 전 주문 취소 중 결제 취소 호출 실패, 늦게 온 결제 완료는 취소 요청으로 되돌림: orderId={}", orderId)
        } else {
            log.info("결제 전 주문 취소: orderId={}, payment={}", orderId, result)
        }
        restoreInventory(orderItems)
        return true
    }

    private fun restoreInventory(orderItems: List<OrderItem>) {
        val reservationIds = orderItems.mapNotNull { it.reservationId }
        if (reservationIds.isNotEmpty()) {
            inventoryCommandPort.cancelReservations(reservationIds)
        } else {
            orderItems.forEach { orderItem ->
                inventoryCommandPort.increaseStock(orderItem.productId, orderItem.quantity)
            }
        }
    }

    companion object {
        private const val PRODUCT_INSUFFICIENT_STOCK = "INV002"
        private val errorBodyReader = ObjectMapper()
    }
}
