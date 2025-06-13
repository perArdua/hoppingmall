package com.hoppingmall.mall.global.vo.password.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordNotMatchedExceptionTest {

    @Test
    fun `PasswordNotMatchedException은 BusinessException을 상속한다`() {
        val exception = PasswordNotMatchedException()
        assertTrue(exception is BusinessException)
    }

    @Test
    fun `PasswordNotMatchedException은 PASSWORD_NOT_MATCHED 에러코드를 사용한다`() {
        val exception = PasswordNotMatchedException()
        assertEquals(PasswordErrorCode.PASSWORD_NOT_MATCHED, exception.errorCode)
        assertEquals("비밀번호가 일치하지 않습니다.", exception.message)
    }
}