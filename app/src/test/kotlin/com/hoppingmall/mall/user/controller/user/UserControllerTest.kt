package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.user.dto.request.user.UpdateUserRequest
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

    private val controller = UserController(
        userCommandService = userCommandService,
        userQueryService = userQueryService
    )

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
