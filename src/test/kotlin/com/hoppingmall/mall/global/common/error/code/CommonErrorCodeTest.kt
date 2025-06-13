package com.hoppingmall.mall.global.common.error.code

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CommonErrorCodeTest {

    @Test
    fun `각 에러코드는 유효한 메시지와 상태코드를 가진다`() {
        val expected = mapOf(
            CommonErrorCode.INVALID_INPUT to HttpStatus.BAD_REQUEST,
            CommonErrorCode.UNAUTHORIZED to HttpStatus.UNAUTHORIZED,
            CommonErrorCode.FORBIDDEN to HttpStatus.FORBIDDEN,
            CommonErrorCode.INTERNAL_ERROR to HttpStatus.INTERNAL_SERVER_ERROR
        )

        expected.forEach { (code, status) ->
            assertNotNull(code.message)
            assertEquals(status, code.status)
        }
    }
}