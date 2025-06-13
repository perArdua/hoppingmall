package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import com.hoppingmall.mall.user.service.user.UserQueryService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserControllerTest {

    private val userCommandService: UserCommandService = mock()
    private val userQueryService: UserQueryService = mock()

    private val controller = UserController(
        userCommandService = userCommandService,
        userQueryService = userQueryService
    )

    @Test
    fun `회원가입 요청이 성공하면 응답 코드와 사용자 정보가 반환된다`() {
        // given
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

        // when
        val response: ApiResponse<SignUpResponse> = controller.signUp(request)

        // then
        assertEquals("SUCCESS", response.code)
        assertEquals("성공", response.message)
        assertEquals(expectedResponse, response.data)
    }

    @Test
    fun `로그인 요청이 성공하면 토큰 정보가 포함된 응답이 반환된다`() {
        // given
        val request = SignInRequest(
            email = "test@example.com",
            password = "password123"
        )

        val expectedResponse = SignInResponse(
            accessToken = "access-token"
        )

        whenever(userQueryService.login(request)).thenReturn(expectedResponse)

        // when
        val response: ApiResponse<SignInResponse> = controller.login(request)

        // then
        assertEquals("SUCCESS", response.code)
        assertEquals("성공", response.message)
        assertEquals(expectedResponse, response.data)
    }
}
