package com.hoppingmall.user.support.fixture

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.domain.Buyer
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.support.withId

fun Buyer.Companion.fixture(
    userId: Long = 1L
): Buyer {
    val user = User.fixture(
        email = Email("buyer@example.com"),
        password = Password("encoded-password"),
        name = "구매자",
        role = Role.BUYER
    ).withId(userId)
    return Buyer.create(user)
}
