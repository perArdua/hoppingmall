package com.hoppingmall.user.common.vo

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

fun interface PasswordMaskingStrategy {
    fun mask(): String
}

object DefaultPasswordMaskingStrategy : PasswordMaskingStrategy {
    override fun mask(): String = "******"
}
