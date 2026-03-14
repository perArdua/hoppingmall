package com.hoppingmall.order.internal

import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/api/v1")
class InternalCartItemController(
    private val cartItemRepository: CartItemRepository
) {

    @GetMapping("/cart-items/aggregate")
    fun aggregateCartByProduct(): ResponseEntity<List<CartAggregationResponse>> {
        val aggregation = cartItemRepository.aggregateCartByProduct()
            .map { CartAggregationResponse(productId = it.getProductId(), buyerCount = it.getBuyerCount()) }
        return ResponseEntity.ok(aggregation)
    }

    data class CartAggregationResponse(
        val productId: Long,
        val buyerCount: Long
    )
}
