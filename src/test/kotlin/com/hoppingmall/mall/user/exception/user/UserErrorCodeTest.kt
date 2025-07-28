package com.hoppingmall.mall.user.exception.user

import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("UserErrorCode")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserErrorCodeTest {

    @Nested
    @DisplayName("에러 코드 검증")
    inner class ErrorCodeValidation {
        @Test
        fun 모든_사용자_에러코드는_유효한_코드와_메시지_상태를_가진다() {
            val expected = mapOf(
                UserErrorCode.USER_ALREADY_EXISTS to Triple("U001", "이미 존재하는 이메일입니다.", HttpStatus.CONFLICT),
                UserErrorCode.LOGIN_FAILED to Triple("U002", "이메일 또는 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
                UserErrorCode.USER_NOT_FOUND to Triple("U003", "존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND)
            )

            expected.forEach { (errorCode, expectedValues) ->
                assertEquals(expectedValues.first, errorCode.code)
                assertEquals(expectedValues.second, errorCode.message)
                assertEquals(expectedValues.third, errorCode.status)
            }
        }

        @Test
        fun 모든_에러코드는_ErrorCode_인터페이스를_구현한다() {
            UserErrorCode.values().forEach { errorCode ->
                assertNotNull(errorCode.code)
                assertNotNull(errorCode.message)
                assertNotNull(errorCode.status)
            }
        }
    }
}