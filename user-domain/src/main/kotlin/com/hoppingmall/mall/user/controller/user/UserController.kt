package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.user.UpdateUserRequest
import com.hoppingmall.mall.user.dto.response.user.UserProfileResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import com.hoppingmall.mall.user.service.user.UserQueryService
import jakarta.validation.Valid
import com.hoppingmall.mall.global.auth.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "회원")
class UserController(
    private val userCommandService: UserCommandService,
    private val userQueryService: UserQueryService
) {

    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<UserProfileResponse> {
        val userId = principal.getUserId()
        val response = userQueryService.getUserProfile(userId)
        return ApiResponse.Companion.success(response)
    }

    @PatchMapping("/me")
    fun updateMyProfile(
        @RequestBody @Valid request: UpdateUserRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<Unit> {
        val userId = principal.getUserId()
        userCommandService.updateUserProfile(userId, request)
        return ApiResponse.Companion.success(Unit)
    }
}