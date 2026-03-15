package com.hoppingmall.product.grpc

import com.hoppingmall.order.grpc.*
import com.hoppingmall.product.review.port.OrderItemInfo
import com.hoppingmall.product.review.port.OrderQueryPort
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

    override fun isDelivered(orderId: Long, buyerId: Long): Boolean {
        return try {
            val response = stub.isDelivered(
                DeliveredCheckRequest.newBuilder()
                    .setOrderId(orderId)
                    .setBuyerId(buyerId)
                    .build()
            )
            response.delivered
        } catch (e: StatusRuntimeException) {
            log.warn("배송 완료 확인 실패: orderId=$orderId, buyerId=$buyerId", e)
            false
        }
    }

    override fun findOrderItemById(orderItemId: Long): OrderItemInfo? {
        return try {
            val response = stub.findOrderItemById(
                OrderItemIdRequest.newBuilder().setOrderItemId(orderItemId).build()
            )
            response.toReviewOrderItemInfo()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) null
            else {
                log.warn("주문 상품 조회 실패: orderItemId=$orderItemId", e)
                null
            }
        }
    }

    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        return try {
            val response = stub.findOrderItemsByOrderId(
                OrderIdRequest.newBuilder().setOrderId(orderId).build()
            )
            response.itemsList.map { it.toReviewOrderItemInfo() }
        } catch (e: StatusRuntimeException) {
            log.warn("주문 상품 목록 조회 실패: orderId=$orderId", e)
            emptyList()
        }
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
