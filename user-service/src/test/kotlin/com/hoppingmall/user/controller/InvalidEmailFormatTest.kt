package com.hoppingmall.user.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.user.common.vo.DefaultPasswordPolicy
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.config.ratelimit.ClientIpResolver
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.response.SignInResponse
import com.hoppingmall.user.service.MembershipCommandService
import com.hoppingmall.user.service.UserCommandServiceImpl
import com.hoppingmall.user.service.UserDomainService
import com.hoppingmall.user.service.UserQueryServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@DisplayName("이메일 형식이 값 객체 검사에 걸리는 회원가입·로그인")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InvalidEmailFormatTest {

    private val objectMapper = ObjectMapper()

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val userRepository = mock<UserRepository>()
        val passwordEncoder = BCryptPasswordEncoder(4)
        val userQueryService = UserQueryServiceImpl(userRepository, PasswordVerifier(passwordEncoder))
        val userCommandService = UserCommandServiceImpl(
            userRepository = userRepository,
            passwordCreator = PasswordCreator(passwordEncoder, DefaultPasswordPolicy()),
            passwordVerifier = PasswordVerifier(passwordEncoder),
            userDomainService = mock<UserDomainService>(),
            membershipCommandService = mock<MembershipCommandService>()
        )
        val authService = mock<AuthService> {
            on { login(any(), any()) } doAnswer {
                userQueryService.authenticate(it.getArgument<SignInRequest>(0))
                SignInResponse("access-token", "refresh-token")
            }
        }
        mockMvc = MockMvcBuilders.standaloneSetup(UserController(userCommandService, userQueryService, authService, ClientIpResolver()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @ParameterizedTest
    @ValueSource(strings = ["o'brien@example.com", "홍길동@example.com", "user@exa_mple.com"])
    fun 값_객체_형식에_맞지_않는_이메일로_가입하면_400이다(email: String) {
        val result = send("/api/v1/users/signup", """{"email":"$email","password":"Password1234","name":"가입자"}""")

        assertThat(result.status).isEqualTo(400)
        assertThat(result.code).isNotEqualTo("S001")
    }

    @ParameterizedTest
    @ValueSource(strings = ["o'brien@example.com", "홍길동@example.com", "user@exa_mple.com"])
    fun 값_객체_형식에_맞지_않는_이메일로_로그인하면_400이다(email: String) {
        val result = send("/api/v1/users/login", """{"email":"$email","password":"Password1234"}""")

        assertThat(result.status).isEqualTo(400)
        assertThat(result.code).isNotEqualTo("S001")
    }

    @ParameterizedTest
    @ValueSource(strings = ["o'brien@example.com", "", "no-at-sign"])
    fun 값_객체는_형식_오류를_400_업무_예외로_알리고_메시지에_이메일_원문을_넣지_않는다(email: String) {
        val thrown = catchThrowable { Email(email) }

        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        assertThat((thrown as BusinessException).errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        if (email.isNotEmpty()) {
            assertThat(thrown.message).doesNotContain(email)
        }
    }

    private fun send(path: String, body: String): Result {
        val response = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andReturn().response
        val code = objectMapper.readTree(response.getContentAsString(Charsets.UTF_8)).path("code").asText()
        return Result(response.status, code)
    }

    private data class Result(val status: Int, val code: String)
}
