package com.hoppingmall.user.common.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("Password VO 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PasswordTest {

    @AfterEach
    fun tearDown() {
        Password.maskingStrategy = DefaultPasswordMaskingStrategy
    }

    @Test
    fun toString은_마스킹된_값을_반환한다() {
        val password = Password("hashed-secret-value")

        assertThat(password.toString()).isEqualTo("******")
    }

    @Test
    fun 커스텀_마스킹_전략을_적용할_수_있다() {
        Password.maskingStrategy = PasswordMaskingStrategy { "***MASKED***" }
        val password = Password("hashed-secret-value")

        assertThat(password.toString()).isEqualTo("***MASKED***")
    }

    @Test
    fun 기본_마스킹_전략은_별표6개를_반환한다() {
        assertThat(DefaultPasswordMaskingStrategy.mask()).isEqualTo("******")
    }

    @Test
    fun 동일한_해시값이면_true를_반환한다() {
        val password = Password("hashed-value")

        assertThat(password.isSameHashedValueWith("hashed-value")).isTrue()
        assertThat(password.isSameHashedValueWith("different-value")).isFalse()
    }
}
