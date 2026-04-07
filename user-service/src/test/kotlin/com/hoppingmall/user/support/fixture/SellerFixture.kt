package com.hoppingmall.user.support.fixture

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.support.withId

fun Seller.Companion.fixture(
    businessNumber: String = "123-45-67890"
): Seller {
    val user = User.fixture(
        email = Email("seller@example.com"),
        password = Password("encoded-password"),
        name = "판매자",
        role = Role.SELLER
    ).withId(1L)
    return Seller.create(user, businessNumber)
}
