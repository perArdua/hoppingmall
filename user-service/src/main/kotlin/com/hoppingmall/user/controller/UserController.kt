package com.hoppingmall.user.controller

import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.dto.response.SignInResponse
import com.hoppingmall.user.dto.response.SignUpResponse
import com.hoppingmall.user.dto.response.UserProfileResponse
import com.hoppingmall.user.exception.user.LoginAttemptsExceededException
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.service.UserQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
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

    private val log = LoggerFactory.getLogger(javaClass)

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

    @ExceptionHandler(LoginAttemptsExceededException::class)
    fun handleLoginAttemptsExceeded(ex: LoginAttemptsExceededException): ResponseEntity<ApiResponse<Unit>> {
        val retryAfterSeconds = (ex.retryAfter.toMillis() + 999) / 1000
        log.warn("BusinessException: code={}, message={}, retryAfterSeconds={}", ex.errorCode.code, ex.errorCode.message, retryAfterSeconds)
        return ResponseEntity.status(ex.errorCode.status)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(ApiResponse.failure(ex.errorCode))
    }
}
