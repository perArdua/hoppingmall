package com.hoppingmall.product.config

import com.hoppingmall.product.support.TestCacheConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("신원은 있고 역할이 부족한 요청의 응답")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class RoleForbiddenResponseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @ParameterizedTest
    @CsvSource("BUYER", "SELLER")
    fun 관리자가_아닌_사용자가_카테고리를_만들면_오류_재전달_없이_403과_권한_없음_본문이다(role: String) {
        val response = mockMvc.post("/api/v1/categories") {
            header("X-User-Id", "9201")
            header("x-user-role", role)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"권한-확인"}"""
        }.andExpect {
            status { isForbidden() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.code") { value("A002") }
        }.andReturn().response

        assertThat(response.errorMessage).isNull()
    }

    @ParameterizedTest
    @CsvSource("BUYER", "SELLER")
    fun 관리자가_아닌_사용자가_관리자_DLQ_통계를_보면_오류_재전달_없이_403과_권한_없음_본문이다(role: String) {
        val response = mockMvc.get("/api/v1/admin/dlq/stats") {
            header("X-User-Id", "9202")
            header("x-user-role", role)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("A002") }
        }.andReturn().response

        assertThat(response.errorMessage).isNull()
    }

    @Test
    fun 구매자가_상품을_등록하면_403과_권한_없음_본문이다() {
        mockMvc.post("/api/v1/products") {
            header("X-User-Id", "9203")
            header("x-user-role", "BUYER")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("A002") }
        }
    }

    @Test
    fun 신원_없이_관리자_입구를_부르면_401과_인증_필요_본문이다() {
        mockMvc.get("/api/v1/admin/dlq/stats").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("A001") }
        }
    }
}
