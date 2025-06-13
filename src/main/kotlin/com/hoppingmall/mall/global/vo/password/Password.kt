package com.hoppingmall.mall.global.vo.password

import com.hoppingmall.mall.global.vo.password.strategy.DefaultPasswordMaskingStrategy
import com.hoppingmall.mall.global.vo.password.strategy.PasswordMaskingStrategy
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Password(
    @Column(name = "password", nullable = false)
    val value: String
) {
    companion object {
        var maskingStrategy: PasswordMaskingStrategy = DefaultPasswordMaskingStrategy
    }

    override fun toString(): String = maskingStrategy.mask()

    fun isSameHashedValueWith(hashed: String): Boolean = this.value == hashed
}