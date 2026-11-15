package com.hoppingmall.common

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.Valid
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ListBodyValidationTestController {
    @PostMapping("/field-error/list")
    fun list(@Valid @RequestBody items: List<FieldErrorTestItem>): Int = items.size
}

@DisplayName("GlobalExceptionHandler 목록 본문 검증 오류 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerListBodyValidationTest {

    private val objectMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val mockMvc = MockMvcBuilders.standaloneSetup(ListBodyValidationTestController())
        .setControllerAdvice(GlobalExceptionHandler(objectMapper))
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    @Test
    fun 목록_본문의_원소_검증에_실패하면_원소_위치와_JSON_이름을_준다() {
        mockMvc.perform(
            post("/field-error/list").contentType(MediaType.APPLICATION_JSON)
                .content("""[{"product_id":1,"quantity":1},{"product_id":null,"quantity":0}]""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("[1].product_id", "[1].quantity")))
            .andExpect(jsonPath("$.errors[*].reason").value(contains("상품 id는 필수입니다.", "수량은 1 이상입니다.")))
    }
}
