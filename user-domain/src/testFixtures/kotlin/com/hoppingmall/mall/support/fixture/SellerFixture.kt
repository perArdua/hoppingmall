package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.User

fun Seller.Companion.fixture(
    businessNumber: String = "123-45-67890"
): Seller {
    val user = User.create(
        email = Email("seller@example.com"),
        password = Password("encoded"),
        name = "판매자",
        role = Role.SELLER
    ).withId(1L)
    return Seller.create(user, businessNumber)
}
