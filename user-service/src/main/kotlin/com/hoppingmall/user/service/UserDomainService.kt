package com.hoppingmall.user.service

import com.hoppingmall.user.common.vo.Email

interface UserDomainService {
    fun validateNewUser(email: Email, rawPassword: String)
}
