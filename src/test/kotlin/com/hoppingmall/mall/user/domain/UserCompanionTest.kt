package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UserCompanionTest {

    @Test
    fun `정적 팩토리 메서드로 유저 객체를 생성할 수 있다`() {
        // given
        val email = Email("user@example.com")
        val password = Password("encryptedPassword")

        // when
        val user = User.create(email, password, "테스트유저", Role.SELLER)

        // then
        assertEquals(email, user.email)
        assertEquals("테스트유저", user.getName())
        assertEquals(Role.SELLER, user.getRole())
    }

    @Test
    fun `역할을 지정하지 않으면 기본값 BUYER로 유저가 생성된다`() {
        // given
        val email = Email("default@example.com")
        val password = Password("pass1234")

        // when
        val user = User.create(email, password, "기본유저")

        // then
        assertEquals(Role.BUYER, user.getRole())
    }
}
