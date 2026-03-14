package com.hoppingmall.product.product.dto.response

import com.hoppingmall.product.product.dto.TopProductProjection
import java.math.BigDecimal

data class TopProductResponse(
    val productId: Long,
    val totalAmount: BigDecimal,
    val totalQuantity: Long
) {
    companion object {
        fun from(projection: TopProductProjection): TopProductResponse {
            return TopProductResponse(
                productId = projection.getProductId(),
                totalAmount = projection.getTotalAmount(),
                totalQuantity = projection.getTotalQuantity()
            )
        }
    }
}
