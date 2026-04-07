package com.hoppingmall.user.support.fixture

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.domain.User

fun User.Companion.fixture(
    email: Email = Email("user@example.com"),
    password: Password = Password("encoded-password"),
    name: String = "테스트유저",
    role: Role = Role.BUYER
): User = User.create(email = email, password = password, name = name, role = role)
