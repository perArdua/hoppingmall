package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.vo.password.service.PasswordVerifier
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.exception.user.UserLoginFailedException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.*

@DisplayName("UserQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserQueryServiceImplTest {

    private val userRepository: UserRepository = mock()
    private val passwordVerifier: PasswordVerifier = mock()

    private val userQueryService = UserQueryServiceImpl(
        userRepository = userRepository,
        passwordVerifier = passwordVerifier
    )

    @Nested
    @DisplayName("authenticate")
    inner class Authenticate {
        @Test
        fun 로그인_성공_시_유저_정보를_반환한다() {
            val rawPassword = "secure123"
            val hashedPassword = Password("encoded")

            val user = User.fixture(
                email = Email("login@example.com"),
                password = hashedPassword,
                role = Role.BUYER
            ).withId(1L)

            whenever(userRepository.findByEmail(user.email)).thenReturn(user)
            whenever(passwordVerifier.matches(rawPassword, hashedPassword)).thenReturn(true)

            val result = userQueryService.authenticate(
                SignInRequest(user.email.value, rawPassword)
            )

            assertEquals(user.id, result.id)
            assertEquals(user.email, result.email)
            assertEquals(user.getRole(), result.getRole())
        }

        @Test
        fun 비밀번호가_일치하지_않으면_예외가_발생한다() {
            val user = User.fixture(
                email = Email("fail@example.com"),
                password = Password("encoded"),
                role = Role.SELLER
            ).withId(2L)

            whenever(userRepository.findByEmail(user.email)).thenReturn(user)
            doThrow(UserLoginFailedException()).whenever(passwordVerifier)
                .assertMatches("wrongpass", user.getPassword())

            assertThrows(UserLoginFailedException::class.java) {
                userQueryService.authenticate(SignInRequest(user.email.value, "wrongpass"))
            }
        }

        @Test
        fun 존재하지_않는_이메일이면_예외가_발생한다() {
            val email = Email("none@example.com")
            whenever(userRepository.findByEmail(email)).thenReturn(null)

            assertThrows(UserLoginFailedException::class.java) {
                userQueryService.authenticate(SignInRequest(email.value, "irrelevant"))
            }
        }
    }
}