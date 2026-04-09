package com.hoppingmall.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

@DisplayName("GlobalExceptionHandler 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    private val methodParameter: MethodParameter =
        MethodParameter(GlobalExceptionHandler::class.java.methods[0], -1)

    @Test
    fun BusinessException_처리_시_ErrorCode_기반_응답을_반환한다() {
        val exception = BusinessException(CommonErrorCode.INVALID_INPUT)

        val response = handler.handleBusinessException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("C001")
        assertThat(response.body?.message).isEqualTo("잘못된 입력입니다.")
        assertThat(response.body?.data).isNull()
    }

    @Test
    fun UNAUTHORIZED_ErrorCode_BusinessException_처리() {
        val exception = BusinessException(CommonErrorCode.UNAUTHORIZED)

        val response = handler.handleBusinessException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.body?.code).isEqualTo("A001")
    }

    @Test
    fun FORBIDDEN_ErrorCode_BusinessException_처리() {
        val exception = BusinessException(CommonErrorCode.FORBIDDEN)

        val response = handler.handleBusinessException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(response.body?.code).isEqualTo("A002")
    }

    @Test
    fun INTERNAL_ERROR_ErrorCode_BusinessException_처리() {
        val exception = BusinessException(CommonErrorCode.INTERNAL_ERROR)

        val response = handler.handleBusinessException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.code).isEqualTo("S001")
    }

    @Test
    fun LOCK_ACQUISITION_FAILED_ErrorCode_BusinessException_처리() {
        val exception = BusinessException(CommonErrorCode.LOCK_ACQUISITION_FAILED)

        val response = handler.handleBusinessException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.code).isEqualTo("S002")
    }

    @Test
    fun MethodArgumentNotValidException_처리_시_첫번째_필드_에러_메시지를_사용한다() {
        val bindingResult = BeanPropertyBindingResult(Any(), "target")
        bindingResult.addError(FieldError("target", "name", "이름은 필수입니다."))
        val exception = MethodArgumentNotValidException(methodParameter, bindingResult)

        val response = handler.handleValidationException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
    }

    @Test
    fun MethodArgumentNotValidException_필드에러가_없으면_기본_메시지를_사용한다() {
        val bindingResult = BeanPropertyBindingResult(Any(), "target")
        val exception = MethodArgumentNotValidException(methodParameter, bindingResult)

        val response = handler.handleValidationException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
    }

    @Test
    fun 일반_Exception_처리_시_INTERNAL_ERROR_응답을_반환한다() {
        val exception = RuntimeException("예상치 못한 오류")

        val response = handler.handleException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.code).isEqualTo(CommonErrorCode.INTERNAL_ERROR.code)
        assertThat(response.body?.message).isEqualTo(CommonErrorCode.INTERNAL_ERROR.message)
    }

    @Test
    fun NullPointerException_처리() {
        val exception = NullPointerException()

        val response = handler.handleException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.code).isEqualTo("S001")
    }
}
