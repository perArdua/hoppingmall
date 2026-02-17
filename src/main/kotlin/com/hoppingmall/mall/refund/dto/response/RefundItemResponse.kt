package com.hoppingmall.mall.refund.dto.response

import com.hoppingmall.mall.refund.domain.RefundItem
import java.math.BigDecimal

data class RefundItemResponse(
    val id: Long,
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val productPrice: BigDecimal,
    val quantity: Int,
    val refundPrice: BigDecimal
) {
    companion object {
        fun from(refundItem: RefundItem): RefundItemResponse {
            return RefundItemResponse(
                id = refundItem.id!!,
                orderItemId = refundItem.orderItemId,
                productId = refundItem.productId,
                productName = refundItem.productName,
                productPrice = refundItem.productPrice,
                quantity = refundItem.quantity,
                refundPrice = refundItem.refundPrice
            )
        }
    }
}
