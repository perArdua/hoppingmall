package com.hoppingmall.mall.global.common.error.handler

import com.hoppingmall.mall.global.common.error.code.CommonErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.global.common.response.ApiResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import kotlin.test.assertEquals

class GlobalExceptionHandlerExtraTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `BusinessException이 BAD_REQUEST 상태로 응답된다`() {
        // given
        val ex = BusinessException(CommonErrorCode.INVALID_INPUT)

        // when
        val response = handler.handleBusinessException(ex)

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(CommonErrorCode.INVALID_INPUT.message, response.body?.message)
    }

    @Test
    fun `fieldErrors가 비어 있으면 기본 메시지로 응답된다`() {
        // given
        val methodParameter = mock(MethodParameter::class.java)
        val bindingResult = BeanPropertyBindingResult("target", "objectName")
        val ex = MethodArgumentNotValidException(methodParameter, bindingResult)

        // when
        val response = handler.handleValidationException(ex)

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("유효성 검증 실패", response.body?.message)
    }

    @Test
    fun `알 수 없는 예외를 처리할 때 응답 포맷은 실패 응답이다`() {
        // given
        val ex = Exception("뭔가 잘못됨")

        // when
        val response = handler.handleException(ex)

        // then
        assertEquals(500, response.statusCode.value())
        assertEquals("알 수 없는 오류가 발생했습니다.", response.body?.message)
        assertEquals("FAIL", response.body?.code)
    }
}
