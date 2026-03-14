package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.vo.password.service.PasswordCreator
import com.hoppingmall.mall.membership.service.MembershipCommandService
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.request.user.UpdateUserRequest
import com.hoppingmall.mall.user.exception.user.UserAlreadyExistsException
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.*

@DisplayName("UserCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserCommandServiceImplTest {

    private val userRepository: UserRepository = mock()
    private val passwordCreator: PasswordCreator = mock()
    private val userDomainService: UserDomainService = mock()
    private val membershipCommandService: MembershipCommandService = mock()
    private val userCommandService = UserCommandServiceImpl(userRepository, passwordCreator, userDomainService, membershipCommandService)

    @Nested
    @DisplayName("signUp")
    inner class SignUp {
        @Test
        fun 회원가입_성공_시_사용자_정보가_저장되고_응답이_반환된다() {
            val captor = argumentCaptor<User>()
            val request = SignUpRequest(
                email = "test@example.com",
                password = "securePass123",
                name = "홍길동",
                role = Role.SELLER
            )

            whenever(passwordCreator.encode(request.password)).thenReturn(Password("hashed"))
            whenever(userRepository.save(captor.capture())).thenAnswer {
                captor.firstValue.withId(1L)
            }

            val response = userCommandService.signUp(request)

            val savedUser = captor.firstValue
            assertEquals(Email("test@example.com"), savedUser.email)
            assertEquals("홍길동", savedUser.getName())
            assertEquals(Role.SELLER, savedUser.getRole())
            assertEquals(1L, response.id)
            verify(userDomainService).validateNewUser(Email(request.email), request.password)
            verify(membershipCommandService).createMembership(1L)
        }

        @Test
        fun 이미_존재하는_이메일이면_예외가_발생한다() {
            val request = SignUpRequest(
                email = "dup@example.com",
                password = "securePass123",
                name = "중복유저",
                role = Role.BUYER
            )

            doThrow(UserAlreadyExistsException()).whenever(userDomainService)
                .validateNewUser(Email(request.email), request.password)

            assertThrows(UserAlreadyExistsException::class.java) {
                userCommandService.signUp(request)
            }
        }
    }

    @Nested
    @DisplayName("updateUserProfile")
    inner class UpdateUserProfile {

        @Test
        fun 존재하는_사용자_정보_수정_성공() {
            val userId = 1L
            val request = UpdateUserRequest("새로운이름", "newPassword123!")
            val user = User.fixture().withId(userId)
            val encodedPassword = Password("encodedNewPassword")

            whenever(userRepository.findNullableById(userId)).thenReturn(user)
            whenever(passwordCreator.encode(request.password!!)).thenReturn(encodedPassword)

            userCommandService.updateUserProfile(userId, request)

            assertEquals("새로운이름", user.getName())
            assertEquals(encodedPassword, user.getPassword())
        }

        @Test
        fun 비밀번호_없이_이름만_수정_성공() {
            val userId = 1L
            val request = UpdateUserRequest("새로운이름", null)
            val user = User.fixture().withId(userId)

            whenever(userRepository.findNullableById(userId)).thenReturn(user)

            userCommandService.updateUserProfile(userId, request)

            assertEquals("새로운이름", user.getName())
        }

        @Test
        fun 존재하지_않는_사용자_ID로_수정_시도_시_예외_발생() {
            val userId = 999L
            val request = UpdateUserRequest("새로운이름", "newPassword123!")

            whenever(userRepository.findNullableById(userId)).thenReturn(null)

            assertThrows(UserNotFoundException::class.java) {
                userCommandService.updateUserProfile(userId, request)
            }
        }
    }
}
