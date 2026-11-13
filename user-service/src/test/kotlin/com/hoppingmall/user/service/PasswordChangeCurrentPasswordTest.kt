package com.hoppingmall.user.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.user.auth.service.RefreshTokenService
import com.hoppingmall.user.common.vo.DefaultPasswordPolicy
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.PasswordNotMatchedException
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.UpdateUserRequest
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
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("비밀번호 변경의 현재 비밀번호 확인")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PasswordChangeCurrentPasswordTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userDomainService: UserDomainService

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    @Mock
    private lateinit var refreshTokenService: RefreshTokenService

    private val passwordEncoder = BCryptPasswordEncoder()

    private lateinit var originalPassword: Password

    private lateinit var user: User

    private lateinit var userCommandService: UserCommandServiceImpl

    @BeforeEach
    fun setUp() {
        userCommandService = UserCommandServiceImpl(
            userRepository = userRepository,
            passwordCreator = PasswordCreator(passwordEncoder, DefaultPasswordPolicy()),
            passwordVerifier = PasswordVerifier(passwordEncoder),
            userDomainService = userDomainService,
            membershipCommandService = membershipCommandService,
            refreshTokenService = refreshTokenService
        )
        originalPassword = Password(passwordEncoder.encode("Password1234"))
        user = User.fixture(password = originalPassword, name = "옛이름").withId(1L)
        whenever(userRepository.findNullableById(1L)).thenReturn(user)
    }

    @Test
    fun 현재_비밀번호_없이_비밀번호를_바꾸면_거절되고_비밀번호는_그대로다() {
        assertThatThrownBy {
            updateWithBody("""{"name":"새이름","password":"NewPassword1234"}""")
        }.isInstanceOf(PasswordNotMatchedException::class.java)

        assertThat(user.getPassword()).isEqualTo(originalPassword)
        verify(refreshTokenService, never()).delete(any())
    }

    @Test
    fun 현재_비밀번호가_틀리면_거절되고_비밀번호는_그대로다() {
        assertThatThrownBy {
            updateWithBody("""{"name":"새이름","password":"NewPassword1234","current_password":"WrongPassword1234"}""")
        }.isInstanceOf(PasswordNotMatchedException::class.java)

        assertThat(user.getPassword()).isEqualTo(originalPassword)
        verify(refreshTokenService, never()).delete(any())
    }

    @Test
    fun 현재_비밀번호가_맞으면_비밀번호를_바꾸고_refresh_token을_지운다() {
        updateWithBody("""{"name":"새이름","password":"NewPassword1234","current_password":"Password1234"}""")

        assertThat(passwordEncoder.matches("NewPassword1234", user.getPassword().value)).isTrue()
        assertThat(user.getName()).isEqualTo("새이름")
        verify(refreshTokenService).delete(1L)
    }

    @Test
    fun 비밀번호를_바꾸지_않는_수정은_현재_비밀번호_없이_이름만_바꾼다() {
        updateWithBody("""{"name":"새이름"}""")

        assertThat(user.getName()).isEqualTo("새이름")
        assertThat(user.getPassword()).isEqualTo(originalPassword)
        verify(refreshTokenService, never()).delete(any())
    }

    private fun updateWithBody(body: String) {
        val request = applicationObjectMapper().readValue(body, UpdateUserRequest::class.java)
        userCommandService.updateUserProfile(1L, request)
    }

    private fun applicationObjectMapper(): ObjectMapper {
        lateinit var objectMapper: ObjectMapper
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
            .run { context -> objectMapper = context.getBean(ObjectMapper::class.java) }
        return objectMapper
    }
}
