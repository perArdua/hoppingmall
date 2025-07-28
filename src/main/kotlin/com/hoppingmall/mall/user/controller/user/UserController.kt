package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.auth.service.AuthService
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.request.user.UpdateUserRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.dto.response.user.UserProfileResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import com.hoppingmall.mall.user.service.user.UserQueryService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userCommandService: UserCommandService,
    private val userQueryService: UserQueryService,
    private val authService: AuthService
) {

    @PostMapping("/signup")
    fun signUp(
        @RequestBody @Valid request: SignUpRequest
    ): ApiResponse<SignUpResponse> {
        val response = userCommandService.signUp(request)
        return ApiResponse.Companion.success(response)
    }

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: SignInRequest
    ): ApiResponse<SignInResponse> {
        val response = authService.login(request)
        return ApiResponse.Companion.success(response)
    }

    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal userDetails: UserDetails
    ): ApiResponse<UserProfileResponse> {
        val userId = userDetails.username.toLong()
        val response = userQueryService.getUserProfile(userId)
        return ApiResponse.Companion.success(response)
    }

    @PatchMapping("/me")
    fun updateMyProfile(
        @RequestBody @Valid request: UpdateUserRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ApiResponse<Unit> {
        val userId = userDetails.username.toLong()
        userCommandService.updateUserProfile(userId, request)
        return ApiResponse.Companion.success(Unit)
    }
}