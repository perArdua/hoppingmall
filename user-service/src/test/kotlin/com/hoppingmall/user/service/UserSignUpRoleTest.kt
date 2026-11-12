package com.hoppingmall.user.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.PasswordPolicy
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.response.SignUpResponse
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("회원가입 요청의 역할")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserSignUpRoleTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var passwordPolicy: PasswordPolicy

    @Mock
    private lateinit var userDomainService: UserDomainService

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    private lateinit var userCommandService: UserCommandServiceImpl

    private lateinit var userCaptor: KArgumentCaptor<User>

    @BeforeEach
    fun setUp() {
        userCommandService = UserCommandServiceImpl(
            userRepository = userRepository,
            passwordCreator = PasswordCreator(passwordEncoder, passwordPolicy),
            userDomainService = userDomainService,
            membershipCommandService = membershipCommandService
        )
        userCaptor = argumentCaptor()
        whenever(passwordEncoder.encode("Password1234")).thenReturn("encoded-password")
        whenever(userRepository.save(userCaptor.capture())).thenAnswer {
            userCaptor.firstValue.withId(1L)
        }
        whenever(membershipCommandService.createMembership(1L)).thenReturn(mock())
    }

    @Test
    fun 가입_요청에_ADMIN_역할을_넣어도_BUYER로_가입된다() {
        val response = signUpWithBody(
            """{"email":"admin-try@example.com","password":"Password1234","name":"관리자시도","role":"ADMIN"}"""
        )

        assertThat(userCaptor.firstValue.getRole()).isEqualTo(Role.BUYER)
        assertThat(response.role).isEqualTo(Role.BUYER)
    }

    @Test
    fun 가입_요청에_SELLER_역할을_넣어도_BUYER로_가입된다() {
        val response = signUpWithBody(
            """{"email":"seller-try@example.com","password":"Password1234","name":"판매자시도","role":"SELLER"}"""
        )

        assertThat(userCaptor.firstValue.getRole()).isEqualTo(Role.BUYER)
        assertThat(response.role).isEqualTo(Role.BUYER)
    }

    @Test
    fun 가입_요청에_역할이_없어도_BUYER로_가입된다() {
        val response = signUpWithBody(
            """{"email":"no-role@example.com","password":"Password1234","name":"역할없음"}"""
        )

        assertThat(userCaptor.firstValue.getRole()).isEqualTo(Role.BUYER)
        assertThat(response.role).isEqualTo(Role.BUYER)
    }

    private fun signUpWithBody(body: String): SignUpResponse {
        val request = applicationObjectMapper().readValue(body, SignUpRequest::class.java)
        return userCommandService.signUp(request)
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
