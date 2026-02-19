package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.wishlist.domain.Wishlist

fun Wishlist.Companion.fixture(
    buyerId: Long = 1L,
    productId: Long = 100L,
): Wishlist {
    return Wishlist.create(buyerId, productId)
}
