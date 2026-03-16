package com.hoppingmall.product.grpc

import com.hoppingmall.order.grpc.*
import com.hoppingmall.product.review.port.OrderItemInfo
import com.hoppingmall.product.review.port.OrderQueryPort
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.client.inject.GrpcClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@Profile("grpc")
class GrpcOrderQueryAdapter(
    @GrpcClient("order-service") private val stub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub
) : OrderQueryPort {

    private val log = LoggerFactory.getLogger(GrpcOrderQueryAdapter::class.java)

    @CircuitBreaker(name = "order-query", fallbackMethod = "isDeliveredFallback")
    @Retry(name = "grpc")
    override fun isDelivered(orderId: Long, buyerId: Long): Boolean {
        val response = stub.isDelivered(
            DeliveredCheckRequest.newBuilder()
                .setOrderId(orderId)
                .setBuyerId(buyerId)
                .build()
        )
        return response.delivered
    }

    @CircuitBreaker(name = "order-query", fallbackMethod = "findOrderItemByIdFallback")
    @Retry(name = "grpc")
    override fun findOrderItemById(orderItemId: Long): OrderItemInfo? {
        val response = stub.findOrderItemById(
            OrderItemIdRequest.newBuilder().setOrderItemId(orderItemId).build()
        )
        return response.toReviewOrderItemInfo()
    }

    @CircuitBreaker(name = "order-query", fallbackMethod = "findOrderItemsByOrderIdFallback")
    @Retry(name = "grpc")
    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        val response = stub.findOrderItemsByOrderId(
            OrderIdRequest.newBuilder().setOrderId(orderId).build()
        )
        return response.itemsList.map { it.toReviewOrderItemInfo() }
    }

    private fun isDeliveredFallback(orderId: Long, buyerId: Long, e: Exception): Boolean {
        log.warn("CB fallback: 배송 완료 확인 실패 orderId=$orderId, buyerId=$buyerId", e)
        return false
    }

    private fun findOrderItemByIdFallback(orderItemId: Long, e: Exception): OrderItemInfo? {
        if (e is StatusRuntimeException && e.status.code == Status.Code.NOT_FOUND) return null
        log.warn("CB fallback: 주문 상품 조회 실패 orderItemId=$orderItemId", e)
        return null
    }

    private fun findOrderItemsByOrderIdFallback(orderId: Long, e: Exception): List<OrderItemInfo> {
        log.warn("CB fallback: 주문 상품 목록 조회 실패 orderId=$orderId", e)
        return emptyList()
    }

    private fun OrderItemResponse.toReviewOrderItemInfo() = OrderItemInfo(
        id = id,
        orderId = orderId,
        sellerId = sellerId,
        productId = productId,
        productName = productName,
        productPrice = productPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO,
        quantity = quantity,
        totalPrice = totalPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
    )
}
