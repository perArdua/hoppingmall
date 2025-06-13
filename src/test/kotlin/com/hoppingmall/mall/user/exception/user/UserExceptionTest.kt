package com.hoppingmall.mall.user.exception.user

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserExceptionTest {

    @Test
    fun `UserException은 BusinessException을 상속한다`() {
        val exception = TestUserException()
        assertTrue(exception is BusinessException)
    }

    @Test
    fun `UserException은 전달받은 에러코드를 사용한다`() {
        val exception = TestUserException()
        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
        assertEquals("존재하지 않는 사용자입니다.", exception.message)
    }

    // Test implementation of UserException
    private class TestUserException : UserException(UserErrorCode.USER_NOT_FOUND)
}