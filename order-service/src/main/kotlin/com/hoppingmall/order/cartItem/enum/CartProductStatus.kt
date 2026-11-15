package com.hoppingmall.order.cartItem.enum

enum class CartProductStatus {
    AVAILABLE, SOLD_OUT, DELETED;

    companion object {
        fun of(productStatus: String?): CartProductStatus? =
            entries.firstOrNull { it != DELETED && it.name == productStatus }
    }
}
