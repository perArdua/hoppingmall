package com.hoppingmall.user.service

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordNotMatchedException
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.exception.user.UserLoginFailedException
import com.hoppingmall.user.exception.user.UserNotFoundException
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("UserQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserQueryServiceImplTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var userQueryService: UserQueryServiceImpl

    @BeforeEach
    fun setUp() {
        userQueryService = UserQueryServiceImpl(
            userRepository = userRepository,
            passwordVerifier = PasswordVerifier(passwordEncoder)
        )
    }

    @Test
    fun 이메일과_비밀번호가_정상이면_사용자를_반환한다() {
        val user = com.hoppingmall.user.domain.User.fixture(
            email = Email("login@example.com"),
            password = Password("encoded-password"),
            role = Role.BUYER
        ).withId(1L)
        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        whenever(passwordEncoder.matches("Password1234", "encoded-password")).thenReturn(true)

        val result = userQueryService.authenticate(SignInRequest(user.email.value, "Password1234"))

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.email).isEqualTo(user.email)
        assertThat(result.getRole()).isEqualTo(Role.BUYER)
    }

    @Test
    fun 사용자가_없으면_로그인_예외가_발생한다() {
        whenever(userRepository.findByEmail(Email("missing@example.com"))).thenReturn(null)

        assertThatThrownBy { userQueryService.authenticate(SignInRequest("missing@example.com", "Password1234")) }
            .isInstanceOf(UserLoginFailedException::class.java)
    }

    @Test
    fun 비밀번호가_다르면_예외가_발생한다() {
        val user = com.hoppingmall.user.domain.User.fixture(
            email = Email("wrong-password@example.com"),
            password = Password("encoded-password")
        ).withId(2L)
        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        whenever(passwordEncoder.matches("WrongPassword1234", "encoded-password")).thenReturn(false)

        assertThatThrownBy { userQueryService.authenticate(SignInRequest(user.email.value, "WrongPassword1234")) }
            .isInstanceOf(PasswordNotMatchedException::class.java)
    }

    @Test
    fun 사용자가_있으면_프로필을_반환한다() {
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.SELLER).withId(3L)
        whenever(userRepository.findNullableById(3L)).thenReturn(user)

        val result = userQueryService.getUserProfile(3L)

        assertThat(result.id).isEqualTo(3L)
        assertThat(result.email).isEqualTo(user.email.value)
        assertThat(result.name).isEqualTo(user.getName())
        assertThat(result.role).isEqualTo(Role.SELLER.name)
    }

    @Test
    fun 사용자가_없으면_프로필_조회_예외가_발생한다() {
        whenever(userRepository.findNullableById(404L)).thenReturn(null)

        assertThatThrownBy { userQueryService.getUserProfile(404L) }
            .isInstanceOf(UserNotFoundException::class.java)

        verify(userRepository).findNullableById(404L)
    }
}
