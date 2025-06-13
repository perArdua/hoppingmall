package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import com.hoppingmall.mall.user.service.user.UserQueryService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userCommandService: UserCommandService,
    private val userQueryService: UserQueryService
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
        val response = userQueryService.login(request)
        return ApiResponse.Companion.success(response)
    }
}