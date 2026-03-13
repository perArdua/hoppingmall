package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.user.domain.User

fun User.Companion.fixture(
    email: Email = Email("user@example.com"),
    password: Password = Password("encoded-password"),
    name: String = "테스트유저",
    role: Role = Role.BUYER
): User {
    return User.create(email, password, name, role)
}
