package com.hoppingmall.global.vo

@JvmInline
value class Email(val value: String) {
    init {
        require(value.contains("@")) { "유효하지 않은 이메일 형식입니다." }
    }
}