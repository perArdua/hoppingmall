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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class UserQueryServiceImplTest {

    private val userRepository: UserRepository = mock()
    private val passwordVerifier: PasswordVerifier = mock()

    private val userQueryService = UserQueryServiceImpl(
        userRepository = userRepository,
        passwordVerifier = passwordVerifier
    )

    @Test
    fun `로그인 성공 시 유저 정보를 반환한다`() {
        // given
        val rawPassword = "secure123"
        val hashedPassword = Password("encoded")

        val user = User.fixture(
            email = Email("login@example.com"),
            password = hashedPassword,
            role = Role.BUYER
        ).withId(1L)

        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        whenever(passwordVerifier.matches(rawPassword, hashedPassword)).thenReturn(true)

        // when
        val result = userQueryService.authenticate(
            SignInRequest(user.email.value, rawPassword)
        )

        // then
        assertEquals(user.id, result.id)
        assertEquals(user.email, result.email)
        assertEquals(user.getRole(), result.getRole())
    }

    @Test
    fun `비밀번호가 일치하지 않으면 예외가 발생한다`() {
        // given
        val user = User.fixture(
            email = Email("fail@example.com"),
            password = Password("encoded"),
            role = Role.SELLER
        ).withId(2L)

        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        doThrow(UserLoginFailedException()).whenever(passwordVerifier)
            .assertMatches("wrongpass", user.getPassword())

        // when & then
        assertThrows(UserLoginFailedException::class.java) {
            userQueryService.authenticate(SignInRequest(user.email.value, "wrongpass"))
        }
    }

    @Test
    fun `존재하지 않는 이메일이면 예외가 발생한다`() {
        // given
        val email = Email("none@example.com")
        whenever(userRepository.findByEmail(email)).thenReturn(null)

        // when & then
        assertThrows(UserLoginFailedException::class.java) {
            userQueryService.authenticate(SignInRequest(email.value, "irrelevant"))
        }
    }
}