package com.hoppingmall.mall.user.controller.user

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.service.user.UserCommandService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userCommandService: UserCommandService,
) {

    @PostMapping("/signup")
    fun signUp(
        @RequestBody @Valid request: SignUpRequest
    ): ApiResponse<SignUpResponse> {
        val response = userCommandService.signUp(request)
        return ApiResponse.Companion.success(response)
    }
}