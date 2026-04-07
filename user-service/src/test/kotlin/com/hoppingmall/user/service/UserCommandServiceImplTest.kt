package com.hoppingmall.user.service

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.PasswordPolicy
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.exception.user.UserAlreadyExistsException
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("UserCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserCommandServiceImplTest {

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

    @BeforeEach
    fun setUp() {
        val passwordCreator = PasswordCreator(passwordEncoder, passwordPolicy)
        userCommandService = UserCommandServiceImpl(
            userRepository = userRepository,
            passwordCreator = passwordCreator,
            userDomainService = userDomainService,
            membershipCommandService = membershipCommandService
        )
    }

    @Test
    fun 회원가입_성공_시_사용자를_저장하고_멤버십을_생성한다() {
        val request = SignUpRequest(
            email = "signup@example.com",
            password = "Password1234",
            name = "홍길동",
            role = Role.SELLER
        )
        val userCaptor = argumentCaptor<User>()
        whenever(passwordEncoder.encode(request.password)).thenReturn("encoded-password")
        whenever(userRepository.save(userCaptor.capture())).thenAnswer {
            userCaptor.firstValue.withId(1L)
        }
        whenever(membershipCommandService.createMembership(1L)).thenReturn(mock())

        val response = userCommandService.signUp(request)

        assertThat(response.id).isEqualTo(1L)
        assertThat(response.email).isEqualTo("signup@example.com")
        assertThat(response.name).isEqualTo("홍길동")
        assertThat(response.role).isEqualTo(Role.SELLER)
        verify(userDomainService).validateNewUser(Email(request.email), request.password)
        verify(membershipCommandService).createMembership(1L)
        assertThat(userCaptor.firstValue.email).isEqualTo(Email("signup@example.com"))
        assertThat(userCaptor.firstValue.getName()).isEqualTo("홍길동")
    }

    @Test
    fun 회원가입_시_중복_사용자면_예외를_전파한다() {
        val request = SignUpRequest(
            email = "duplicate@example.com",
            password = "Password1234",
            name = "중복회원",
            role = Role.BUYER
        )
        doThrow(UserAlreadyExistsException()).whenever(userDomainService)
            .validateNewUser(Email(request.email), request.password)

        assertThatThrownBy { userCommandService.signUp(request) }
            .isInstanceOf(UserAlreadyExistsException::class.java)
    }

    @Test
    fun 프로필_수정_시_이름과_비밀번호를_함께_변경한다() {
        val user = User.fixture().withId(1L)
        whenever(userRepository.findNullableById(1L)).thenReturn(user)
        whenever(passwordEncoder.encode("NewPassword1234")).thenReturn("encoded-new-password")

        userCommandService.updateUserProfile(1L, UpdateUserRequest(name = "새이름", password = "NewPassword1234"))

        assertThat(user.getName()).isEqualTo("새이름")
        assertThat(user.getPassword()).isEqualTo(Password("encoded-new-password"))
    }

    @Test
    fun 프로필_수정_시_비밀번호가_없으면_이름만_변경한다() {
        val user = User.fixture().withId(2L)
        val originalPassword = user.getPassword()
        whenever(userRepository.findNullableById(2L)).thenReturn(user)

        userCommandService.updateUserProfile(2L, UpdateUserRequest(name = "이름만변경", password = null))

        assertThat(user.getName()).isEqualTo("이름만변경")
        assertThat(user.getPassword()).isEqualTo(originalPassword)
    }

    @Test
    fun 프로필_수정_대상_사용자가_없으면_예외가_발생한다() {
        whenever(userRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { userCommandService.updateUserProfile(999L, UpdateUserRequest(name = "없음", password = "Password1234")) }
            .isInstanceOf(UserNotFoundException::class.java)
    }
}
