package com.hoppingmall.mall.global.vo.password.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeakPasswordExceptionTest {

    @Test
    fun `WeakPasswordException은 BusinessException을 상속한다`() {
        val exception = WeakPasswordException()
        assertTrue(exception is BusinessException)
    }

    @Test
    fun `WeakPasswordException은 WEAK_PASSWORD 에러코드를 사용한다`() {
        val exception = WeakPasswordException()
        assertEquals(PasswordErrorCode.WEAK_PASSWORD, exception.errorCode)
        assertEquals("비밀번호가 정책을 만족하지 않습니다.", exception.message)
    }
}