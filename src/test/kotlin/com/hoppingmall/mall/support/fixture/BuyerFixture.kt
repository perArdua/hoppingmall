package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.user.domain.Buyer
import com.hoppingmall.mall.user.domain.User

fun Buyer.Companion.fixture(
    user: User
): Buyer {
    return Buyer.create(user)
}
