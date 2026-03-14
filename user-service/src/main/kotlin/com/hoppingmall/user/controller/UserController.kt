package com.hoppingmall.user.controller

import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.user.common.ApiResponse
import com.hoppingmall.user.common.UserPrincipal
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.dto.response.SignInResponse
import com.hoppingmall.user.dto.response.SignUpResponse
import com.hoppingmall.user.dto.response.UserProfileResponse
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.service.UserQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "회원")
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
        return ApiResponse.success(response)
    }

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: SignInRequest
    ): ApiResponse<SignInResponse> {
        val response = authService.login(request)
        return ApiResponse.success(response)
    }

    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<UserProfileResponse> {
        val userId = principal.getUserId()
        val response = userQueryService.getUserProfile(userId)
        return ApiResponse.success(response)
    }

    @PatchMapping("/me")
    fun updateMyProfile(
        @RequestBody @Valid request: UpdateUserRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<Unit> {
        val userId = principal.getUserId()
        userCommandService.updateUserProfile(userId, request)
        return ApiResponse.success(Unit)
    }
}
