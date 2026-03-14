package com.hoppingmall.mall.global.vo.email

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Email(
    @Column(name = "email", nullable = false, unique = true)
    val value: String
) {
    init {
        require(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$").matches(value)) {
            "유효하지 않은 이메일 형식입니다: $value"
        }
    }

    override fun toString(): String = value
}