package com.hoppingmall.product.category.controller

import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.category.service.CategoryQueryService
import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@DisplayName("카테고리 엔드포인트 보안 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class CategoryControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var categoryCommandService: CategoryCommandService

    @MockitoBean
    private lateinit var categoryQueryService: CategoryQueryService

    private val categoryJson = """{"name":"전자기기","parentId":null}"""

    @Nested
    @DisplayName("POST /api/v1/categories")
    inner class CreateCategory {

        @Test
        fun ADMIN_역할로_카테고리_생성_성공() {
            mockMvc.post("/api/v1/categories") {
                contentType = MediaType.APPLICATION_JSON
                content = categoryJson
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isCreated() } }
        }

        @Test
        fun SELLER_역할로_카테고리_생성_시_403_응답() {
            mockMvc.post("/api/v1/categories") {
                contentType = MediaType.APPLICATION_JSON
                content = categoryJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_카테고리_생성_시_403_응답() {
            mockMvc.post("/api/v1/categories") {
                contentType = MediaType.APPLICATION_JSON
                content = categoryJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun 인증되지_않은_카테고리_생성_요청_시_403_응답() {
            mockMvc.post("/api/v1/categories") {
                contentType = MediaType.APPLICATION_JSON
                content = categoryJson
            }.andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/categories/{categoryId}")
    inner class UpdateCategory {

        @Test
        fun ADMIN_역할로_카테고리_수정_성공() {
            mockMvc.put("/api/v1/categories/1") {
                contentType = MediaType.APPLICATION_JSON
                content = categoryJson
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun SELLER_역할로_카테고리_수정_시_403_응답() {
            mockMvc.put("/api/v1/categories/1") {
                contentType = MediaType.APPLICATION_JSON
                content = categoryJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/categories/{categoryId}")
    inner class DeleteCategory {

        @Test
        fun ADMIN_역할로_카테고리_삭제_성공() {
            mockMvc.delete("/api/v1/categories/1") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isNoContent() } }
        }

        @Test
        fun SELLER_역할로_카테고리_삭제_시_403_응답() {
            mockMvc.delete("/api/v1/categories/1") {
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isForbidden() } }
        }
    }
}
