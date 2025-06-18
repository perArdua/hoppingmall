package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.dto.request.user.SignInRequest

interface UserQueryService {
    fun authenticate(request: SignInRequest): User
}
