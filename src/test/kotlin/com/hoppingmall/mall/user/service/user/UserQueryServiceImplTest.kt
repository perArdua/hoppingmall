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
import com.hoppingmall.mall.user.dto.response.user.UserProfileResponse
import com.hoppingmall.mall.user.exception.user.UserLoginFailedException
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
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

    @Nested
    @DisplayName("getUserProfile")
    inner class GetUserProfile {
        @Test
        fun 존재하는_사용자_ID로_프로필_조회_성공() {
            val userId = 1L
            val user = User.fixture().withId(userId)

            whenever(userRepository.findNullableById(userId)).thenReturn(user)

            val result = userQueryService.getUserProfile(userId)

            assertEquals(user.id, result.id)
            assertEquals(user.email.value, result.email)
            assertEquals(user.getName(), result.name)
            assertEquals(user.getRole().name, result.role)
            verify(userRepository).findNullableById(userId)
        }

        @Test
        fun 존재하지_않는_사용자_ID로_프로필_조회_시_예외_발생() {
            val userId = 999L

            whenever(userRepository.findNullableById(userId)).thenReturn(null)

            assertThrows(UserNotFoundException::class.java) {
                userQueryService.getUserProfile(userId)
            }

            verify(userRepository).findNullableById(userId)
        }
    }
}