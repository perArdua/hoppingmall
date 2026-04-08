package com.hoppingmall.product.inventory.controller

import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@DisplayName("재고 엔드포인트 보안 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class InventoryControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var inventoryCommandService: InventoryCommandService

    @MockitoBean
    private lateinit var inventoryQueryService: InventoryQueryService

    private val inventoryJson = """{"productId":1,"quantity":100}"""

    @Nested
    @DisplayName("POST /api/v1/inventories")
    inner class InitStock {

        @Test
        fun SELLER_역할로_재고_초기화_성공() {
            mockMvc.post("/api/v1/inventories") {
                contentType = MediaType.APPLICATION_JSON
                content = inventoryJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isCreated() } }
        }

        @Test
        fun ADMIN_역할로_재고_초기화_성공() {
            mockMvc.post("/api/v1/inventories") {
                contentType = MediaType.APPLICATION_JSON
                content = inventoryJson
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isCreated() } }
        }

        @Test
        fun BUYER_역할로_재고_초기화_시_403_응답() {
            mockMvc.post("/api/v1/inventories") {
                contentType = MediaType.APPLICATION_JSON
                content = inventoryJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun 인증되지_않은_재고_초기화_요청_시_403_응답() {
            mockMvc.post("/api/v1/inventories") {
                contentType = MediaType.APPLICATION_JSON
                content = inventoryJson
            }.andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/inventories/{productId}")
    inner class UpdateStock {

        private val updateJson = """{"quantity":50}"""

        @Test
        fun SELLER_역할로_재고_수정_성공() {
            mockMvc.patch("/api/v1/inventories/1") {
                contentType = MediaType.APPLICATION_JSON
                content = updateJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun ADMIN_역할로_재고_수정_성공() {
            mockMvc.patch("/api/v1/inventories/1") {
                contentType = MediaType.APPLICATION_JSON
                content = updateJson
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun BUYER_역할로_재고_수정_시_403_응답() {
            mockMvc.patch("/api/v1/inventories/1") {
                contentType = MediaType.APPLICATION_JSON
                content = updateJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }
    }
}
