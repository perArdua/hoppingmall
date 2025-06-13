package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserTest {

    @Test
    fun `User 생성 시 입력된 값이 정상적으로 매핑된다`() {
        val user = User.fixture(
            email = Email("test@example.com"),
            password = Password("encoded-password"),
            name = "홍길동",
            role = Role.SELLER
        )

        assertEquals(Email("test@example.com"), user.email)
        assertEquals("홍길동", user.getName())
        assertEquals(Role.SELLER, user.getRole())
    }

    @Test
    fun `이름을 updateName으로 변경하면 새로운 이름이 반영된다`() {
        val user = User.fixture(name = "기존이름")
        user.updateName("변경된이름")
        assertEquals("변경된이름", user.getName())
    }

    @Test
    fun `비밀번호를 updatePassword로 변경할 수 있다`() {
        val user = User.fixture(password = Password("oldPassword"))
        user.updatePassword(Password("newPassword"))
        assertEquals("newPassword", user.getPassword().value)
    }
}
