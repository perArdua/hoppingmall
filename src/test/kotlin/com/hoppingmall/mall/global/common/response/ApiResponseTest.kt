package com.hoppingmall.mall.global.common.response

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.http.HttpStatus

@DisplayName("ApiResponse")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ApiResponseTest {

    @Nested
    @DisplayName("success")
    inner class Success {
        @Test
        fun 성공_응답은_코드_SUCCESS와_함께_데이터가_포함되어야_한다() {
            val result = ApiResponse.success("테스트 데이터")

            assertEquals("SUCCESS", result.code)
            assertEquals("성공", result.message)
            assertEquals("테스트 데이터", result.data)
        }
    }

    @Nested
    @DisplayName("failure")
    inner class Failure {
        @Test
        fun 실패_응답은_코드_FAIL과_함께_메시지가_포함되어야_한다() {
            val result = ApiResponse.failure<String>("에러 발생")

            assertEquals("FAIL", result.code)
            assertEquals("에러 발생", result.message)
            assertNull(result.data)
        }

        @Test
        fun ErrorCode를_사용하는_실패_응답은_ErrorCode의_코드와_메시지를_포함해야_한다() {
            val errorCode = object : ErrorCode {
                override val code: String = "USER_NOT_FOUND"
                override val message: String = "사용자를 찾을 수 없습니다"
                override val status = HttpStatus.NOT_FOUND
            }

            val result = ApiResponse.failure<String>(errorCode)

            assertEquals("USER_NOT_FOUND", result.code)
            assertEquals("사용자를 찾을 수 없습니다", result.message)
            assertNull(result.data)
        }

        @Test
        fun 일반_메시지_기반_실패_응답은_data가_null이어야_한다() {
            val result = ApiResponse.failure<String>("에러 메시지")
            assertEquals("FAIL", result.code)
            assertEquals("에러 메시지", result.message)
            assertNull(result.data)
        }

        @Test
        fun ErrorCode_기반_실패_응답도_data가_null이어야_한다() {
            val errorCode = object : ErrorCode {
                override val code = "INVALID"
                override val message = "잘못된 요청"
                override val status = HttpStatus.BAD_REQUEST
            }

            val result = ApiResponse.failure<String>(errorCode)
            assertEquals("INVALID", result.code)
            assertEquals("잘못된 요청", result.message)
            assertNull(result.data)
        }
    }
}
