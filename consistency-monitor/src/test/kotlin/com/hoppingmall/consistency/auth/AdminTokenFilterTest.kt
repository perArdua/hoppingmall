package com.hoppingmall.consistency.auth

import io.jsonwebtoken.security.WeakKeyException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("AdminTokenFilter")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminTokenFilterTest {

    @Test
    fun 서명_키가_32바이트보다_짧으면_만들_수_없다() {
        assertThatThrownBy { AdminTokenFilter("short-secret") }.isInstanceOf(WeakKeyException::class.java)
    }
}
