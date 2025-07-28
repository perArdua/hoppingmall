package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.user.domain.Buyer
import com.hoppingmall.mall.user.domain.User

fun Buyer.Companion.fixture(): Buyer {
    val user = User.fixture(
        email = Email("buyer@example.com"),
        password = Password("encoded"),
        name = "구매자",
        role = Role.BUYER
    )
    return Buyer.create(user)
}
