package com.hoppingmall.mall.global.vo.password.policy

interface PasswordPolicy {
    fun validate(raw: String)
}
