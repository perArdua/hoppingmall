package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.user.dto.request.user.LoginRequest
import com.hoppingmall.mall.user.dto.response.user.LoginResponse

interface UserQueryService {
    fun login(request: LoginRequest): LoginResponse
}
