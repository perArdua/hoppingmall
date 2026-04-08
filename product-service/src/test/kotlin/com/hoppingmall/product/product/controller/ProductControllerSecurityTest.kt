package com.hoppingmall.product.product.controller

import com.hoppingmall.product.common.file.FileUploadService
import com.hoppingmall.product.product.service.BulkImportService
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.product.service.ProductImageService
import com.hoppingmall.product.product.service.ProductQueryService
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@DisplayName("상품 엔드포인트 보안 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class ProductControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var productCommandService: ProductCommandService

    @MockitoBean
    private lateinit var productQueryService: ProductQueryService

    @MockitoBean
    private lateinit var productImageService: ProductImageService

    @MockitoBean
    private lateinit var fileUploadService: FileUploadService

    @MockitoBean
    private lateinit var bulkImportService: BulkImportService

    private val productJson = """{"name":"test","price":1000,"description":"desc","categoryId":1}"""

    @Nested
    @DisplayName("POST /api/v1/products")
    inner class CreateProduct {

        @Test
        fun SELLER_역할로_상품_생성_성공() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = productJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun ADMIN_역할로_상품_생성_성공() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = productJson
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun BUYER_역할로_상품_생성_시_403_응답() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = productJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun 인증되지_않은_상품_생성_요청_시_403_응답() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = productJson
            }.andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/products/{productId}")
    inner class UpdateProduct {

        @Test
        fun SELLER_역할로_상품_수정_성공() {
            mockMvc.put("/api/v1/products/1") {
                contentType = MediaType.APPLICATION_JSON
                content = productJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun BUYER_역할로_상품_수정_시_403_응답() {
            mockMvc.put("/api/v1/products/1") {
                contentType = MediaType.APPLICATION_JSON
                content = productJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{productId}")
    inner class DeleteProduct {

        @Test
        fun SELLER_역할로_상품_삭제_성공() {
            mockMvc.delete("/api/v1/products/1") {
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun BUYER_역할로_상품_삭제_시_403_응답() {
            mockMvc.delete("/api/v1/products/1") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/products/bulk/validate")
    inner class BulkValidate {

        private val csvFile = MockMultipartFile("file", "products.csv", "text/csv", "name,price\ntest,1000".toByteArray())

        @Test
        fun SELLER_역할로_대량_등록_검증_성공() {
            mockMvc.multipart("/api/v1/products/bulk/validate") {
                file(csvFile)
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun BUYER_역할로_대량_등록_검증_시_403_응답() {
            mockMvc.multipart("/api/v1/products/bulk/validate") {
                file(csvFile)
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/products/bulk/import")
    inner class BulkImport {

        private val csvFile = MockMultipartFile("file", "products.csv", "text/csv", "name,price\ntest,1000".toByteArray())

        @Test
        fun SELLER_역할로_대량_등록_실행_성공() {
            mockMvc.multipart("/api/v1/products/bulk/import") {
                file(csvFile)
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun BUYER_역할로_대량_등록_실행_시_403_응답() {
            mockMvc.multipart("/api/v1/products/bulk/import") {
                file(csvFile)
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }
    }
}
