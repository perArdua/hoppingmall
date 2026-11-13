package com.hoppingmall.user.service

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.user.auth.service.RefreshTokenService
import com.hoppingmall.user.common.vo.DefaultPasswordPolicy
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("비밀번호 변경에서 현재 비밀번호가 틀렸을 때의 응답 상태")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PasswordChangeWrongCurrentPasswordStatusTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userDomainService: UserDomainService

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    @Mock
    private lateinit var refreshTokenService: RefreshTokenService

    private val passwordEncoder = BCryptPasswordEncoder()

    private val exceptionHandler = GlobalExceptionHandler()

    private lateinit var originalPassword: Password

    private lateinit var user: User

    private lateinit var userCommandService: UserCommandServiceImpl

    @BeforeEach
    fun setUp() {
        val passwordVerifier = PasswordVerifier(passwordEncoder)
        userCommandService = UserCommandServiceImpl(
            userRepository = userRepository,
            passwordCreator = PasswordCreator(passwordEncoder, DefaultPasswordPolicy()),
            passwordVerifier = passwordVerifier,
            userDomainService = userDomainService,
            membershipCommandService = membershipCommandService,
            refreshTokenService = refreshTokenService
        )
        originalPassword = Password(passwordEncoder.encode("Password1234"))
        user = User.fixture(email = Email("change@example.com"), password = originalPassword).withId(1L)
    }

    @Test
    fun 현재_비밀번호가_틀리면_401이_아니라_400과_로그인_실패와_다른_코드로_거절한다() {
        whenever(userRepository.findNullableById(1L)).thenReturn(user)

        val response = responseOf {
            userCommandService.updateUserProfile(
                1L,
                UpdateUserRequest(name = "새이름", password = "NewPassword1234", currentPassword = "WrongPassword1234")
            )
        }

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.code).isEqualTo("P003")
        assertThat(user.getPassword()).isEqualTo(originalPassword)
        verify(refreshTokenService, never()).delete(any())
    }

    @Test
    fun 현재_비밀번호_없이_비밀번호를_바꾸면_400과_같은_코드로_거절한다() {
        whenever(userRepository.findNullableById(1L)).thenReturn(user)

        val response = responseOf {
            userCommandService.updateUserProfile(
                1L,
                UpdateUserRequest(name = "새이름", password = "NewPassword1234")
            )
        }

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.code).isEqualTo("P003")
        assertThat(user.getPassword()).isEqualTo(originalPassword)
        verify(refreshTokenService, never()).delete(any())
    }

    @Test
    fun 로그인에서_비밀번호가_틀리면_로그인_실패와_같은_401_U002다() {
        whenever(userRepository.findByEmail(Email("change@example.com"))).thenReturn(user)
        val userQueryService = UserQueryServiceImpl(userRepository, PasswordVerifier(passwordEncoder))

        val response = responseOf {
            userQueryService.authenticate(SignInRequest("change@example.com", "WrongPassword1234"))
        }

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.body!!.code).isEqualTo("U002")
    }

    private fun responseOf(action: () -> Unit): ResponseEntity<ApiResponse<Unit>> {
        val exception = catchThrowableOfType(BusinessException::class.java) { action() }
        assertThat(exception).isNotNull
        return exceptionHandler.handleBusinessException(exception)
    }
}
