package com.hoppingmall.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.MethodParameter
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

@DisplayName("GlobalExceptionHandler 빈의 JSON 이름 규칙 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerNamingStrategyBeanTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
        .withBean(GlobalExceptionHandler::class.java)

    private val bodyParameter = MethodParameter(
        FieldErrorTestController::class.java.getMethod("body", FieldErrorTestBody::class.java), 0
    )

    private fun invalidProductName(): MethodArgumentNotValidException {
        val bindingResult = BeanPropertyBindingResult(Any(), "fieldErrorTestBody")
        bindingResult.addError(FieldError("fieldErrorTestBody", "productName", "상품 이름은 필수입니다."))
        return MethodArgumentNotValidException(bodyParameter, bindingResult)
    }

    @Test
    fun 서비스의_SNAKE_CASE_설정을_따라_본문_필드_이름을_바꾼다() {
        contextRunner
            .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE")
            .run { context ->
                val response = context.getBean(GlobalExceptionHandler::class.java)
                    .handleValidationException(invalidProductName())

                assertThat(response.body?.errors).containsExactly(FieldErrorDetail("product_name", "상품 이름은 필수입니다."))
            }
    }

    @Test
    fun 이름_규칙이_없는_서비스는_본문_필드_이름을_그대로_준다() {
        contextRunner.run { context ->
            val response = context.getBean(GlobalExceptionHandler::class.java)
                .handleValidationException(invalidProductName())

            assertThat(response.body?.errors).containsExactly(FieldErrorDetail("productName", "상품 이름은 필수입니다."))
        }
    }
}
