package com.hoppingmall.user.common.vo

import com.hoppingmall.user.exception.user.InvalidEmailException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Email(
    @Column(name = "email", nullable = false, unique = true)
    val value: String
) {
    init {
        if (!FORMAT_REGEX.matches(value)) {
            throw InvalidEmailException()
        }
    }

    override fun toString(): String = value

    companion object {
        const val FORMAT = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        private val FORMAT_REGEX = Regex(FORMAT)
    }
}
