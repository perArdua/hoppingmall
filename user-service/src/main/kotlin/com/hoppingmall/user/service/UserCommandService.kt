package com.hoppingmall.user.service

import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.dto.response.SignUpResponse

interface UserCommandService {
    fun signUp(request: SignUpRequest): SignUpResponse
    fun updateUserProfile(userId: Long, request: UpdateUserRequest)
}
