package com.hoppingmall.mall.global.auth.exception

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class InvalidTokenExceptionTest {
    @Test
    fun 생성자_호출_정상() {
        val exception = InvalidTokenException()
        assertNotNull(exception)
    }
} 