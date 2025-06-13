package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.User

fun Seller.Companion.fixture(
    user: User,
    businessNumber: String = "123-45-67890"
): Seller {
    return Seller.create(user, businessNumber)
}
