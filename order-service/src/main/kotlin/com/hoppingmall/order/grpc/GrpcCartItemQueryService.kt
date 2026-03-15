package com.hoppingmall.order.grpc

import net.devh.boot.grpc.server.service.GrpcService
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository

@GrpcService
class GrpcCartItemQueryService(
    private val cartItemRepository: CartItemRepository
) : CartItemQueryServiceGrpcKt.CartItemQueryServiceCoroutineImplBase() {

    override suspend fun aggregateCartByProduct(request: Empty): CartAggregateResponse {
        val cartAggregations = cartItemRepository.aggregateCartByProduct()
        return cartAggregateResponse {
            aggregations += cartAggregations.map { agg ->
                cartAggregation {
                    productId = agg.getProductId()
                    buyerCount = agg.getBuyerCount()
                }
            }
        }
    }
}
