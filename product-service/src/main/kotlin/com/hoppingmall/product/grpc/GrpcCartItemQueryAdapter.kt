package com.hoppingmall.product.grpc

import com.hoppingmall.order.grpc.CartItemQueryServiceGrpc
import com.hoppingmall.order.grpc.Empty
import com.hoppingmall.product.statistics.port.CartAggregation
import com.hoppingmall.product.statistics.port.CartItemQueryPort
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.client.inject.GrpcClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("grpc")
class GrpcCartItemQueryAdapter(
    @GrpcClient("order-service") private val stub: CartItemQueryServiceGrpc.CartItemQueryServiceBlockingStub
) : CartItemQueryPort {

    private val log = LoggerFactory.getLogger(GrpcCartItemQueryAdapter::class.java)

    override fun aggregateCartByProduct(): List<CartAggregation> {
        return try {
            val response = stub.aggregateCartByProduct(Empty.getDefaultInstance())
            response.aggregationsList.map {
                CartAggregation(
                    productId = it.productId,
                    buyerCount = it.buyerCount
                )
            }
        } catch (e: StatusRuntimeException) {
            log.warn("장바구니 통계 조회 실패", e)
            emptyList()
        }
    }
}
