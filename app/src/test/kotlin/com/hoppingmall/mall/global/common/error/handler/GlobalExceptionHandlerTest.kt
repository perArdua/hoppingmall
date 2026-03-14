package com.hoppingmall.mall.global.common.error.handler

import com.hoppingmall.mall.global.common.error.code.CommonErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.global.common.response.ApiResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import kotlin.test.assertEquals

@DisplayName("GlobalExceptionHandler")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerExtraTest {

    private val handler = GlobalExceptionHandler()

    @Nested
    @DisplayName("handleBusinessException")
    inner class HandleBusinessException {
        @Test
        fun BusinessException이_BAD_REQUEST_상태로_응답된다() {
            val ex = BusinessException(CommonErrorCode.INVALID_INPUT)

            val response = handler.handleBusinessException(ex)

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(CommonErrorCode.INVALID_INPUT.message, response.body?.message)
        }
    }

    @Nested
    @DisplayName("handleValidationException")
    inner class HandleValidationException {
        @Test
        fun fieldErrors가_비어_있으면_기본_메시지로_응답된다() {
            val methodParameter = mock(MethodParameter::class.java)
            val bindingResult = BeanPropertyBindingResult("target", "objectName")
            val ex = MethodArgumentNotValidException(methodParameter, bindingResult)

            val response = handler.handleValidationException(ex)

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals("유효성 검증 실패", response.body?.message)
        }
    }

    @Nested
    @DisplayName("handleException")
    inner class HandleException {
        @Test
        fun 알_수_없는_예외를_처리할_때_응답_포맷은_실패_응답이다() {
            val ex = Exception("뭔가 잘못됨")

            val response = handler.handleException(ex)

            assertEquals(500, response.statusCode.value())
            assertEquals("알 수 없는 오류가 발생했습니다.", response.body?.message)
            assertEquals("FAIL", response.body?.code)
        }
    }
}
