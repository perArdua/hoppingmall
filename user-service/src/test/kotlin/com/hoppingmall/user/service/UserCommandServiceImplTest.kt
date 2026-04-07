package com.hoppingmall.user.service

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.PasswordPolicy
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.dto.response.MembershipResponse
import com.hoppingmall.user.exception.user.UserAlreadyExistsException
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("UserCommandServiceImpl 단위 테스트")
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
        whenever(membershipCommandService.createMembership(1L)).thenReturn(
            MembershipResponse(
                id = 1L,
                userId = 1L,
                grade = MembershipGrade.BRONZE,
                gradeName = MembershipGrade.BRONZE.gradeName,
                totalSpent = BigDecimal.ZERO,
                pointEarningRate = MembershipGrade.BRONZE.pointEarningRate,
                discountRate = MembershipGrade.BRONZE.discountRate,
                nextGrade = MembershipGrade.SILVER,
                amountToNextGrade = MembershipGrade.SILVER.requiredAmount,
                createdAt = LocalDateTime.now(),
                updatedAt = null
            )
        )

        val response = userCommandService.signUp(request)

        assertEquals(1L, response.id)
        assertEquals("signup@example.com", response.email)
        assertEquals("홍길동", response.name)
        assertEquals(Role.SELLER, response.role)
        verify(userDomainService).validateNewUser(Email(request.email), request.password)
        verify(membershipCommandService).createMembership(1L)
        assertEquals(Email("signup@example.com"), userCaptor.firstValue.email)
        assertEquals("홍길동", userCaptor.firstValue.getName())
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

        assertThrows<UserAlreadyExistsException> {
            userCommandService.signUp(request)
        }
    }

    @Test
    fun 프로필_수정_시_이름과_비밀번호를_함께_변경한다() {
        val user = User.fixture().withId(1L)
        whenever(userRepository.findNullableById(1L)).thenReturn(user)
        whenever(passwordEncoder.encode("NewPassword1234")).thenReturn("encoded-new-password")

        userCommandService.updateUserProfile(1L, UpdateUserRequest(name = "새이름", password = "NewPassword1234"))

        assertEquals("새이름", user.getName())
        assertEquals(Password("encoded-new-password"), user.getPassword())
    }

    @Test
    fun 프로필_수정_시_비밀번호가_없으면_이름만_변경한다() {
        val user = User.fixture().withId(2L)
        val originalPassword = user.getPassword()
        whenever(userRepository.findNullableById(2L)).thenReturn(user)

        userCommandService.updateUserProfile(2L, UpdateUserRequest(name = "이름만변경", password = null))

        assertEquals("이름만변경", user.getName())
        assertEquals(originalPassword, user.getPassword())
    }

    @Test
    fun 프로필_수정_대상_사용자가_없으면_예외가_발생한다() {
        whenever(userRepository.findNullableById(999L)).thenReturn(null)

        assertThrows<UserNotFoundException> {
            userCommandService.updateUserProfile(999L, UpdateUserRequest(name = "없음", password = "Password1234"))
        }
    }
}
