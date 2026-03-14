package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import kotlin.test.assertEquals

@DisplayName("UserCompanion")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserCompanionTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 정적_팩토리_메서드로_유저_객체를_생성할_수_있다() {
            val email = Email("user@example.com")
            val password = Password("encryptedPassword")

            val user = User.create(email, password, "테스트유저", Role.SELLER)

            assertEquals(email, user.email)
            assertEquals("테스트유저", user.getName())
            assertEquals(Role.SELLER, user.getRole())
        }

        @Test
        fun 역할을_지정하지_않으면_기본값_BUYER로_유저가_생성된다() {
            val email = Email("default@example.com")
            val password = Password("pass1234")

            val user = User.create(email, password, "기본유저")

            assertEquals(Role.BUYER, user.getRole())
        }
    }
}
