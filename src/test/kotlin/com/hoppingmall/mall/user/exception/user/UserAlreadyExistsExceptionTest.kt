package com.hoppingmall.mall.user.exception.user

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserAlreadyExistsExceptionTest {

    @Test
    fun `UserAlreadyExistsException은 UserException을 상속한다`() {
        val exception = UserAlreadyExistsException()
        assertTrue(exception is UserException)
    }

    @Test
    fun `UserAlreadyExistsException은 USER_ALREADY_EXISTS 에러코드를 사용한다`() {
        val exception = UserAlreadyExistsException()
        assertEquals(UserErrorCode.USER_ALREADY_EXISTS, exception.errorCode)
        assertEquals("이미 존재하는 이메일입니다.", exception.message)
    }
}