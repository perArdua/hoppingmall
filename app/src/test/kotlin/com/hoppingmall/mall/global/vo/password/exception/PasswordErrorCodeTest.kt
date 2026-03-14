package com.hoppingmall.mall.global.vo.password.exception

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PasswordErrorCodeTest {

    @Test
    fun `모든 비밀번호 에러코드는 유효한 코드와 메시지, 상태를 가진다`() {
        val expected = mapOf(
            PasswordErrorCode.WEAK_PASSWORD to Triple("P001", "비밀번호가 정책을 만족하지 않습니다.", HttpStatus.BAD_REQUEST),
            PasswordErrorCode.PASSWORD_NOT_MATCHED to Triple("P002", "비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED)
        )

        expected.forEach { (errorCode, expectedValues) ->
            assertEquals(expectedValues.first, errorCode.code)
            assertEquals(expectedValues.second, errorCode.message)
            assertEquals(expectedValues.third, errorCode.status)
        }
    }

    @Test
    fun `모든 에러코드는 ErrorCode 인터페이스를 구현한다`() {
        PasswordErrorCode.values().forEach { errorCode ->
            assertNotNull(errorCode.code)
            assertNotNull(errorCode.message)
            assertNotNull(errorCode.status)
        }
    }
}