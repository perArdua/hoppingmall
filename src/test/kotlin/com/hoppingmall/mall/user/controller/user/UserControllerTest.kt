package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.auth.service.AuthService
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.request.user.UpdateUserRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.dto.response.user.UserProfileResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import com.hoppingmall.mall.user.service.user.UserQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.*
import com.hoppingmall.mall.global.auth.UserPrincipal

@DisplayName("UserController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserControllerTest {

    private val userCommandService: UserCommandService = mock()
    private val userQueryService: UserQueryService = mock()
    private val authService: AuthService = mock()

    private val controller = UserController(
        userCommandService = userCommandService,
        userQueryService = userQueryService,
        authService = authService
    )

    @Nested
    @DisplayName("signUp")
    inner class SignUp {
        @Test
        fun 회원가입_요청이_성공하면_응답_코드와_사용자_정보가_반환된다() {
            val request = SignUpRequest(
                email = "test@example.com",
                password = "password123",
                name = "테스트",
                role = Role.SELLER
            )

            val expectedResponse = SignUpResponse(
                id = 1L,
                email = "test@example.com",
                name = "테스트",
                role = Role.SELLER
            )

            whenever(userCommandService.signUp(request)).thenReturn(expectedResponse)

            val response: ApiResponse<SignUpResponse> = controller.signUp(request)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(expectedResponse, response.data)
        }
    }

    @Nested
    @DisplayName("login")
    inner class Login {
        @Test
        fun 로그인_요청이_성공하면_토큰_정보가_포함된_응답이_반환된다() {
            val request = SignInRequest(
                email = "test@example.com",
                password = "password123"
            )

            val expectedResponse = SignInResponse(
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )

            whenever(authService.login(request)).thenReturn(expectedResponse)

            val response: ApiResponse<SignInResponse> = controller.login(request)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(expectedResponse, response.data)
        }
    }

    @Nested
    @DisplayName("getMyProfile")
    inner class GetMyProfile {
        @Test
        fun 인증된_사용자의_프로필_조회_성공() {
            val userId = 1L
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")
            val profileResponse = UserProfileResponse(userId, "test@example.com", "홍길동", Role.BUYER.name)

            whenever(userQueryService.getUserProfile(userId)).thenReturn(profileResponse)

            val result = controller.getMyProfile(principal)

            assertEquals(ApiResponse.success(profileResponse), result)
            verify(userQueryService).getUserProfile(userId)
        }
    }

    @Nested
    @DisplayName("updateMyProfile")
    inner class UpdateMyProfile {
        @Test
        fun 인증된_사용자의_프로필_수정_성공() {
            val userId = 1L
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")
            val request = UpdateUserRequest("새로운이름", "newPassword123!")

            doNothing().whenever(userCommandService).updateUserProfile(userId, request)

            val result = controller.updateMyProfile(request, principal)

            assertEquals(ApiResponse.success(Unit), result)
            verify(userCommandService).updateUserProfile(userId, request)
        }

        @Test
        fun 비밀번호_없이_이름만_수정_성공() {
            val userId = 1L
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")
            val request = UpdateUserRequest("새로운이름", null)

            doNothing().whenever(userCommandService).updateUserProfile(userId, request)

            val result = controller.updateMyProfile(request, principal)

            assertEquals(ApiResponse.success(Unit), result)
            verify(userCommandService).updateUserProfile(userId, request)
        }
    }
}
