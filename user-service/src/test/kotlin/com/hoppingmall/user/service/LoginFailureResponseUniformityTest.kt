package com.hoppingmall.user.service

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("로그인 실패 응답으로 이메일 가입 여부가 드러나지 않음")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LoginFailureResponseUniformityTest {

    @Mock
    private lateinit var userRepository: UserRepository

    private val passwordEncoder = spy(BCryptPasswordEncoder())

    private val exceptionHandler = GlobalExceptionHandler()

    private lateinit var userQueryService: UserQueryServiceImpl

    @BeforeEach
    fun setUp() {
        userQueryService = UserQueryServiceImpl(userRepository, PasswordVerifier(passwordEncoder))
    }

    @Test
    fun 없는_이메일과_틀린_비밀번호는_같은_상태_코드_문구로_거절된다() {
        val user = User.fixture(
            email = Email("member@example.com"),
            password = Password(BCryptPasswordEncoder().encode("Password1234"))
        ).withId(1L)
        whenever(userRepository.findByEmail(Email("member@example.com"))).thenReturn(user)
        whenever(userRepository.findByEmail(Email("missing@example.com"))).thenReturn(null)

        val wrongPassword = responseOf {
            userQueryService.authenticate(SignInRequest("member@example.com", "WrongPassword1234"))
        }
        val missingEmail = responseOf {
            userQueryService.authenticate(SignInRequest("missing@example.com", "WrongPassword1234"))
        }

        assertThat(missingEmail.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(wrongPassword.statusCode).isEqualTo(missingEmail.statusCode)
        assertThat(wrongPassword.body!!.code).isEqualTo(missingEmail.body!!.code)
        assertThat(wrongPassword.body!!.message).isEqualTo(missingEmail.body!!.message)
    }

    @Test
    fun 없는_이메일이어도_비밀번호_해시와_한_번_비교한다() {
        whenever(userRepository.findByEmail(Email("missing@example.com"))).thenReturn(null)

        responseOf {
            userQueryService.authenticate(SignInRequest("missing@example.com", "Password1234"))
        }

        verify(passwordEncoder, times(1)).matches(eq("Password1234"), any())
    }

    private fun responseOf(action: () -> Unit): ResponseEntity<ApiResponse<Unit>> {
        val exception = catchThrowableOfType(BusinessException::class.java) { action() }
        assertThat(exception).isNotNull
        return exceptionHandler.handleBusinessException(exception)
    }
}
