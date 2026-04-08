package com.hoppingmall.product.common.file.controller

import com.hoppingmall.product.common.file.FileUploadService
import com.hoppingmall.product.common.file.dto.FileUploadResponse
import com.hoppingmall.product.product.dto.response.ProductImageResponse
import com.hoppingmall.product.product.service.ProductImageService
import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart

@DisplayName("파일 업로드 보안 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class FileUploadControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var fileUploadService: FileUploadService

    @MockitoBean
    private lateinit var productImageService: ProductImageService

    private val mockFile = MockMultipartFile("file", "test.jpg", "image/jpeg", "content".toByteArray())

    @Nested
    @DisplayName("/api/v1/files/upload")
    inner class FileUpload {

        @Test
        fun SELLER_역할로_파일_업로드_성공() {
            whenever(fileUploadService.uploadFile(any())).thenReturn(
                FileUploadResponse(fileUrl = "/uploads/product/test.jpg", fileName = "test.jpg", fileSize = 7L, domain = "product")
            )

            mockMvc.multipart("/api/v1/files/upload") {
                file(mockFile)
                param("domain", "product")
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect {
                status { isCreated() }
            }
        }

        @Test
        fun ADMIN_역할로_파일_업로드_성공() {
            whenever(fileUploadService.uploadFile(any())).thenReturn(
                FileUploadResponse(fileUrl = "/uploads/product/test.jpg", fileName = "test.jpg", fileSize = 7L, domain = "product")
            )

            mockMvc.multipart("/api/v1/files/upload") {
                file(mockFile)
                param("domain", "product")
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect {
                status { isCreated() }
            }
        }

        @Test
        fun BUYER_역할로_파일_업로드_시_403_응답() {
            mockMvc.multipart("/api/v1/files/upload") {
                file(mockFile)
                param("domain", "product")
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        fun 인증되지_않은_요청_시_403_응답() {
            mockMvc.multipart("/api/v1/files/upload") {
                file(mockFile)
                param("domain", "product")
            }.andExpect {
                status { isForbidden() }
            }
        }
    }

    @Nested
    @DisplayName("/api/v1/products/images/upload")
    inner class ProductImageUpload {

        @Test
        fun SELLER_역할로_상품_이미지_업로드_성공() {
            whenever(productImageService.uploadProductImage(any())).thenReturn(
                ProductImageResponse(imageUrl = "/uploads/product/test.jpg", fileName = "test.jpg", fileSize = 7L)
            )

            mockMvc.multipart("/api/v1/products/images/upload") {
                file(MockMultipartFile("image", "test.jpg", "image/jpeg", "content".toByteArray()))
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        fun ADMIN_역할로_상품_이미지_업로드_성공() {
            whenever(productImageService.uploadProductImage(any())).thenReturn(
                ProductImageResponse(imageUrl = "/uploads/product/test.jpg", fileName = "test.jpg", fileSize = 7L)
            )

            mockMvc.multipart("/api/v1/products/images/upload") {
                file(MockMultipartFile("image", "test.jpg", "image/jpeg", "content".toByteArray()))
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        fun BUYER_역할로_상품_이미지_업로드_시_403_응답() {
            mockMvc.multipart("/api/v1/products/images/upload") {
                file(MockMultipartFile("image", "test.jpg", "image/jpeg", "content".toByteArray()))
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        fun 인증되지_않은_상품_이미지_업로드_요청_시_403_응답() {
            mockMvc.multipart("/api/v1/products/images/upload") {
                file(MockMultipartFile("image", "test.jpg", "image/jpeg", "content".toByteArray()))
            }.andExpect {
                status { isForbidden() }
            }
        }
    }
}
