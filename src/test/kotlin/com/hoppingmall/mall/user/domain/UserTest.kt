package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals

@DisplayName("User")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserTest {

    @Nested
    @DisplayName("생성")
    inner class Creation {
        @Test
        fun User_생성_시_입력된_값이_정상적으로_매핑된다() {
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
    }

    @Nested
    @DisplayName("updateName")
    inner class UpdateName {
        @Test
        fun 이름을_updateName으로_변경하면_새로운_이름이_반영된다() {
            val user = User.fixture(name = "기존이름")
            user.updateName("변경된이름")
            assertEquals("변경된이름", user.getName())
        }
    }

    @Nested
    @DisplayName("updatePassword")
    inner class UpdatePassword {
        @Test
        fun 비밀번호를_updatePassword로_변경할_수_있다() {
            val user = User.fixture(password = Password("oldPassword"))
            user.updatePassword(Password("newPassword"))
            assertEquals("newPassword", user.getPassword().value)
        }
    }
}
