package com.hoppingmall.product.grpc

import com.hoppingmall.order.grpc.OrderIdRequest
import com.hoppingmall.order.grpc.OrderQueryServiceGrpc
import com.hoppingmall.product.statistics.port.OrderItemInfo
import com.hoppingmall.product.statistics.port.OrderItemQueryPort
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import net.devh.boot.grpc.client.inject.GrpcClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@Profile("grpc")
class GrpcOrderItemQueryAdapter(
    @GrpcClient("order-service") private val stub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub
) : OrderItemQueryPort {

    private val log = LoggerFactory.getLogger(GrpcOrderItemQueryAdapter::class.java)

    @CircuitBreaker(name = "order-item-query", fallbackMethod = "findByOrderIdFallback")
    @Retry(name = "grpc")
    override fun findByOrderId(orderId: Long): List<OrderItemInfo> {
        val response = stub.findOrderItemsByOrderId(
            OrderIdRequest.newBuilder().setOrderId(orderId).build()
        )
        return response.itemsList.map {
            OrderItemInfo(
                id = it.id,
                orderId = it.orderId,
                productId = it.productId,
                quantity = it.quantity,
                totalPrice = it.totalPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
            )
        }
    }

    private fun findByOrderIdFallback(orderId: Long, e: Exception): List<OrderItemInfo> {
        log.warn("CB fallback: 주문 상품 조회 실패 orderId=$orderId", e)
        return emptyList()
    }
}
