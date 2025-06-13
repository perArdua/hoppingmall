package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse

interface UserQueryService {
    fun login(request: SignInRequest): SignInResponse
}
