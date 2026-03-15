package com.hoppingmall.payment.grpc

import com.hoppingmall.order.grpc.OrderIdRequest
import com.hoppingmall.order.grpc.OrderQueryServiceGrpc
import com.hoppingmall.payment.port.OrderItemInfo
import com.hoppingmall.payment.port.OrderQueryPort
import com.hoppingmall.payment.port.exception.OrderItemQueryFailedException
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

    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        return try {
            val response = stub.findOrderItemsByOrderId(
                OrderIdRequest.newBuilder().setOrderId(orderId).build()
            )
            response.itemsList.map {
                OrderItemInfo(
                    id = it.id,
                    productId = it.productId,
                    quantity = it.quantity,
                    totalPrice = it.totalPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
                )
            }
        } catch (e: StatusRuntimeException) {
            log.error("주문 상품 조회 실패: orderId=$orderId", e)
            throw OrderItemQueryFailedException(orderId, e)
        }
    }
}
