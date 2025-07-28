package com.hoppingmall.mall.global.common.error.code

import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("CommonErrorCode")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CommonErrorCodeTest {

    @Nested
    @DisplayName("에러 코드 검증")
    inner class ErrorCodeValidation {
        @Test
        fun 각_에러코드는_유효한_메시지와_상태코드를_가진다() {
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
}