package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserControllerTest {

    private val userCommandService: UserCommandService = mock()

    private val controller = UserController(
        userCommandService = userCommandService,
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
}
