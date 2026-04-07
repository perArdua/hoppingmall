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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("UserQueryServiceImpl 단위 테스트")
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
    fun authenticate는_이메일과_비밀번호가_정상이면_사용자를_반환한다() {
        val user = com.hoppingmall.user.domain.User.fixture(
            email = Email("login@example.com"),
            password = Password("encoded-password"),
            role = Role.BUYER
        ).withId(1L)
        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        whenever(passwordEncoder.matches("Password1234", "encoded-password")).thenReturn(true)

        val result = userQueryService.authenticate(SignInRequest(user.email.value, "Password1234"))

        assertEquals(1L, result.id)
        assertEquals(user.email, result.email)
        assertEquals(Role.BUYER, result.getRole())
    }

    @Test
    fun authenticate는_사용자가_없으면_예외가_발생한다() {
        whenever(userRepository.findByEmail(Email("missing@example.com"))).thenReturn(null)

        assertThrows<UserLoginFailedException> {
            userQueryService.authenticate(SignInRequest("missing@example.com", "Password1234"))
        }
    }

    @Test
    fun authenticate는_비밀번호가_다르면_예외가_발생한다() {
        val user = com.hoppingmall.user.domain.User.fixture(
            email = Email("wrong-password@example.com"),
            password = Password("encoded-password")
        ).withId(2L)
        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        whenever(passwordEncoder.matches("WrongPassword1234", "encoded-password")).thenReturn(false)

        assertThrows<PasswordNotMatchedException> {
            userQueryService.authenticate(SignInRequest(user.email.value, "WrongPassword1234"))
        }
    }

    @Test
    fun getUserProfile은_사용자가_있으면_프로필을_반환한다() {
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.SELLER).withId(3L)
        whenever(userRepository.findNullableById(3L)).thenReturn(user)

        val result = userQueryService.getUserProfile(3L)

        assertEquals(3L, result.id)
        assertEquals(user.email.value, result.email)
        assertEquals(user.getName(), result.name)
        assertEquals(Role.SELLER.name, result.role)
    }

    @Test
    fun getUserProfile은_사용자가_없으면_예외가_발생한다() {
        whenever(userRepository.findNullableById(404L)).thenReturn(null)

        assertThrows<UserNotFoundException> {
            userQueryService.getUserProfile(404L)
        }

        verify(userRepository).findNullableById(404L)
    }
}
