package com.hoppingmall.user.common.vo

interface PasswordPolicy {
    fun validate(raw: String)
}

class DefaultPasswordPolicy : PasswordPolicy {
    override fun validate(raw: String) {
        if (raw.length < 8) {
            throw WeakPasswordException()
        }
    }
}
