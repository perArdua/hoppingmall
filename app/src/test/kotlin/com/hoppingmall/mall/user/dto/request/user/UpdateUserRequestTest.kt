package com.hoppingmall.mall.user.dto.request.user

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores

@DisplayName("UpdateUserRequest")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UpdateUserRequestTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Nested
    @DisplayName("유효성 검증")
    inner class 유효성_검증 {

        @Nested
        @DisplayName("성공 케이스")
        inner class 성공_케이스 {

            @ParameterizedTest
            @CsvSource(
                value = [
                    "홍길동, newPassword123!",
                    "김철수, null",
                    "이영희, ",
                    "박민수, anotherPassword456!"
                ]
            )
            fun `유효한 name과 password로 검증 성공`(name: String, password: String?) {
                val request = UpdateUserRequest(
                    name = name,
                    password = if (password == "null") null else password
                )
                
                val violations = validator.validate(request)
                assert(violations.isEmpty())
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        inner class 실패_케이스 {

            @ParameterizedTest
            @CsvSource(
                value = [
                    "'', newPassword123!",
                    "'   ', newPassword123!",
                    "'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', newPassword123!"
                ]
            )
            fun `name이 비정상적이면 검증 실패`(name: String, password: String) {
                val request = UpdateUserRequest(name, password)
                val violations = validator.validate(request)
                assert(violations.isNotEmpty())
            }
        }
    }
}