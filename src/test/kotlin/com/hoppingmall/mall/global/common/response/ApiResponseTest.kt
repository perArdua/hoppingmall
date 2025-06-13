package com.hoppingmall.mall.global.common.response

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ApiResponseTest {

    @Test
    fun `성공 응답은 코드 SUCCESS와 함께 데이터가 포함되어야 한다`() {
        val result = ApiResponse.success("테스트 데이터")

        assertEquals("SUCCESS", result.code)
        assertEquals("성공", result.message)
        assertEquals("테스트 데이터", result.data)
    }

    @Test
    fun `실패 응답은 코드 FAIL과 함께 메시지가 포함되어야 한다`() {
        val result = ApiResponse.failure<String>("에러 발생")

        assertEquals("FAIL", result.code)
        assertEquals("에러 발생", result.message)
        assertNull(result.data)
    }

    @Test
    fun `ErrorCode를 사용하는 실패 응답은 ErrorCode의 코드와 메시지를 포함해야 한다`() {
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
    fun `일반 메시지 기반 실패 응답은 data가 null이어야 한다`() {
        val result = ApiResponse.failure<String>("에러 메시지")
        assertEquals("FAIL", result.code)
        assertEquals("에러 메시지", result.message)
        assertNull(result.data)
    }

    @Test
    fun `ErrorCode 기반 실패 응답도 data가 null이어야 한다`() {
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
