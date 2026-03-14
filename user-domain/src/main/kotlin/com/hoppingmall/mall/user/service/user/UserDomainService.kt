package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.vo.email.Email

interface UserDomainService {
    fun validateNewUser(email: Email, rawPassword: String)
}