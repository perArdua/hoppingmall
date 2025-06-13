package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse

interface UserCommandService {
    fun signUp(request: SignUpRequest): SignUpResponse
}
