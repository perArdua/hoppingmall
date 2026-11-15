package com.hoppingmall.product.config

import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("product 요청 오류 응답의 필드 정보")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class RequestErrorFieldsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun 필수_쿼리가_빠지면_400과_빠진_인자_이름을_알린다() {
        mockMvc.get("/api/v1/admin/product-statistics/hourly") {
            header("X-User-Id", "9401")
            header("x-user-role", "ADMIN")
            param("date", "2026-11-01")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value("productId") }
        }
    }

    @Test
    fun 본문_값의_형식이_틀리면_400과_틀린_필드의_JSON_이름을_알린다() {
        mockMvc.post("/api/v1/categories") {
            header("X-User-Id", "9402")
            header("x-user-role", "ADMIN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"형식-확인","parent_category_id":"abc"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value("parent_category_id") }
        }
    }

    @Test
    fun 본문_JSON이_깨지면_400과_입력_오류다() {
        mockMvc.post("/api/v1/categories") {
            header("X-User-Id", "9403")
            header("x-user-role", "ADMIN")
            contentType = MediaType.APPLICATION_JSON
            content = "{"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }
    }

    @Test
    fun 없는_경로는_404와_REQ001이다() {
        mockMvc.get("/api/v1/no-such-path") {
            header("X-User-Id", "9404")
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("REQ001") }
        }
    }
}
