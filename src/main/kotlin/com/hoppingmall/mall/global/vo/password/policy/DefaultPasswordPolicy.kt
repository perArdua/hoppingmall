package com.hoppingmall.mall.global.vo.password.policy

import com.hoppingmall.mall.global.vo.password.exception.WeakPasswordException

class DefaultPasswordPolicy : PasswordPolicy {
    override fun validate(raw: String) {
        if (raw.length < 8) {
            throw WeakPasswordException()
        }
    }
}