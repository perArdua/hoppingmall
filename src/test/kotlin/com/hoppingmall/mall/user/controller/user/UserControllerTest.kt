package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.auth.service.AuthService
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import com.hoppingmall.mall.user.service.user.UserQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("UserController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserControllerTest {

    private val userCommandService: UserCommandService = mock()
    private val authService: AuthService = mock()

    private val controller = UserController(
        userCommandService = userCommandService,
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
}
