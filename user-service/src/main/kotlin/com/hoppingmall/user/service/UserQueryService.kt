package com.hoppingmall.user.service

import com.hoppingmall.user.domain.User
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.response.UserProfileResponse

interface UserQueryService {
    fun authenticate(request: SignInRequest): User
    fun getUserProfile(userId: Long): UserProfileResponse
}
