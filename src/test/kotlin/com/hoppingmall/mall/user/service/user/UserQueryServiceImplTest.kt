package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.vo.password.PasswordVerifier
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.LoginRequest
import com.hoppingmall.mall.user.exception.user.UserLoginFailedException
import com.hoppingmall.mall.global.jwt.TokenProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserQueryServiceImplTest {

    private val userRepository: UserRepository = mock()
    private val tokenProvider: TokenProvider = mock()
    private val passwordVerifier: PasswordVerifier = mock()

    private val userQueryService = UserQueryServiceImpl(userRepository, tokenProvider, passwordVerifier)

    @Test
    fun `로그인 성공 시 토큰을 반환한다`() {
        // given
        val rawPassword = "securePass123"
        val hashedPassword = Password("encodedPassword")

        val user = User.create(
            email = Email("login@example.com"),
            password = hashedPassword,
            name = "로그인유저",
            role = Role.BUYER
        ).withId(1L)

        whenever(userRepository.findByEmail(Email("login@example.com"))).thenReturn(user)
        whenever(passwordVerifier.matches(rawPassword, hashedPassword)).thenReturn(true)
        whenever(tokenProvider.generateToken(user.id!!, user.getRole())).thenReturn("jwt-token")

        // when
        val response = userQueryService.login(
            LoginRequest(email = "login@example.com", password = rawPassword)
        )

        // then
        assertEquals("jwt-token", response.accessToken)
    }

    @Test
    fun `존재하지 않는 이메일로 로그인 시 예외 발생`() {
        whenever(userRepository.findByEmail(Email("nope@example.com"))).thenReturn(null)

        assertThrows(UserLoginFailedException::class.java) {
            userQueryService.login(LoginRequest("nope@example.com", "pass1234"))
        }
    }

    @Test
    fun `비밀번호 틀릴 경우 로그인 실패`() {
        val rawPassword = "wrongPassword"
        val hashedPassword = Password("encodedPassword")

        val user = User.create(
            email = Email("user@example.com"),
            password = hashedPassword,
            name = "비번틀림",
            role = Role.BUYER
        ).withId(1L)

        whenever(userRepository.findByEmail(Email("user@example.com"))).thenReturn(user)
        whenever(passwordVerifier.matches(rawPassword, hashedPassword)).thenReturn(false)

        assertThrows(UserLoginFailedException::class.java) {
            userQueryService.login(LoginRequest("user@example.com", rawPassword))
        }
    }
}
