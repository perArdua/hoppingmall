package com.hoppingmall.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class FieldErrorTestItem(
    @field:NotNull(message = "상품 id는 필수입니다.")
    val productId: Long?,
    @field:Min(value = 1, message = "수량은 1 이상입니다.")
    val quantity: Int
)

data class FieldErrorTestBody(
    @field:NotBlank(message = "상품 이름은 필수입니다.")
    val productName: String,
    @field:Min(value = 1, message = "단가는 1 이상입니다.")
    val unitPrice: Int,
    @field:Valid
    @field:NotEmpty(message = "항목은 하나 이상입니다.")
    val items: List<FieldErrorTestItem>
)

class FieldErrorTestQuery {
    @field:Min(value = 1, message = "쪽 크기는 1 이상입니다.")
    var pageSize: Int = 20
}

@RestController
class FieldErrorTestController {
    @PostMapping("/field-error/body")
    fun body(@Valid @RequestBody body: FieldErrorTestBody): String = body.productName

    @GetMapping("/field-error/query")
    fun query(@Valid @ModelAttribute query: FieldErrorTestQuery): Int = query.pageSize

    @GetMapping("/field-error/param")
    fun param(@RequestParam("page_size") @Min(value = 1, message = "쪽 크기는 1 이상입니다.") pageSize: Int): Int = pageSize

    @GetMapping("/field-error/required")
    fun required(@RequestParam("quantity") quantity: Int): Int = quantity

    @GetMapping("/field-error/business")
    fun business(): String = throw BusinessException(CommonErrorCode.FORBIDDEN)

    @GetMapping("/field-error/ok")
    fun ok(): ApiResponse<String> = ApiResponse.success("ok")
}

@DisplayName("GlobalExceptionHandler 검증 오류 필드 정보 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerFieldErrorTest {

    private val snakeCaseMapper: ObjectMapper =
        jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val snakeCaseMvc = mockMvc(snakeCaseMapper)

    private val camelCaseMvc = mockMvc(jacksonObjectMapper())

    private fun mockMvc(objectMapper: ObjectMapper): MockMvc =
        MockMvcBuilders.standaloneSetup(FieldErrorTestController())
            .setControllerAdvice(GlobalExceptionHandler(objectMapper))
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()

    private fun postBody(mvc: MockMvc, json: String) =
        mvc.perform(post("/field-error/body").contentType(MediaType.APPLICATION_JSON).content(json))

    @Test
    fun 본문_검증에_실패하면_400_C001과_JSON_이름의_필드와_이유를_준다() {
        postBody(snakeCaseMvc, """{"product_name":" ","unit_price":0,"items":[{"product_id":null,"quantity":1}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.message").value("잘못된 입력입니다."))
            .andExpect(jsonPath("$.errors[*].field").value(contains("items[0].product_id", "product_name", "unit_price")))
            .andExpect(jsonPath("$.errors[*].reason").value(contains("상품 id는 필수입니다.", "상품 이름은 필수입니다.", "단가는 1 이상입니다.")))
    }

    @Test
    fun camelCase_서비스의_본문_검증_실패는_camelCase_이름을_준다() {
        postBody(camelCaseMvc, """{"productName":" ","unitPrice":1,"items":[{"productId":3,"quantity":0}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("items[0].quantity", "productName")))
    }

    @Test
    fun 본문_값의_형식이_틀리면_그_필드를_준다() {
        postBody(snakeCaseMvc, """{"product_name":"a","unit_price":"abc","items":[{"product_id":1,"quantity":1}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("unit_price")))
            .andExpect(jsonPath("$.errors[0].reason").value("형식이 올바르지 않습니다."))
    }

    @Test
    fun 본문_목록_안_값의_형식이_틀리면_목록_위치를_포함한_필드를_준다() {
        postBody(snakeCaseMvc, """{"product_name":"a","unit_price":1,"items":[{"product_id":1,"quantity":1},{"product_id":2,"quantity":"x"}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[*].field").value(contains("items[1].quantity")))
    }

    @Test
    fun 본문의_필수_필드가_없으면_그_필드를_준다() {
        postBody(snakeCaseMvc, """{"unit_price":1,"items":[{"product_id":1,"quantity":1}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("product_name")))
    }

    @Test
    fun 본문_JSON_문법이_깨지면_errors_없이_C001을_준다() {
        postBody(snakeCaseMvc, "{")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors").doesNotExist())
    }

    @Test
    fun 쿼리_객체_검증에_실패하면_쿼리_파라미터_이름을_준다() {
        snakeCaseMvc.perform(get("/field-error/query").param("pageSize", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("pageSize")))
            .andExpect(jsonPath("$.errors[0].reason").value("쪽 크기는 1 이상입니다."))
    }

    @Test
    fun 쿼리_객체의_형_변환에_실패하면_변환_예외_문구_대신_형식_오류를_준다() {
        snakeCaseMvc.perform(get("/field-error/query").param("pageSize", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[*].field").value(contains("pageSize")))
            .andExpect(jsonPath("$.errors[0].reason").value("형식이 올바르지 않습니다."))
    }

    @Test
    fun 쿼리_파라미터_제약에_걸리면_500이_아니라_400_C001과_파라미터_이름을_준다() {
        snakeCaseMvc.perform(get("/field-error/param").param("page_size", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("page_size")))
            .andExpect(jsonPath("$.errors[0].reason").value("쪽 크기는 1 이상입니다."))
    }

    @Test
    fun 필수_쿼리_파라미터가_없으면_그_이름을_준다() {
        snakeCaseMvc.perform(get("/field-error/required"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("quantity")))
            .andExpect(jsonPath("$.errors[0].reason").value("필수 값입니다."))
    }

    @Test
    fun 쿼리_파라미터_형_변환에_실패하면_그_이름을_준다() {
        snakeCaseMvc.perform(get("/field-error/required").param("quantity", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[*].field").value(contains("quantity")))
            .andExpect(jsonPath("$.errors[0].reason").value("형식이 올바르지 않습니다."))
    }

    @Test
    fun 필드와_무관한_오류와_성공_응답에는_errors_키가_없다() {
        snakeCaseMvc.perform(get("/field-error/business"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("A002"))
            .andExpect(jsonPath("$.errors").doesNotExist())

        snakeCaseMvc.perform(get("/field-error/ok"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.errors").doesNotExist())
    }
}
