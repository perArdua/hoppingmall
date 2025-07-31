package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.dto.response.ProductResponse
import com.hoppingmall.mall.product.service.ProductQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime

@DisplayName("ProductController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductControllerTest {

    private val productQueryService: ProductQueryService = mock()
    private val controller = ProductController(productQueryService)

    @Nested
    @DisplayName("getProducts")
    inner class GetProducts {
        @Test
        fun 상품_목록_조회_성공() {
            val pageable = PageRequest.of(0, 10)
            val productResponses = listOf(
                ProductResponse(
                    id = 1L,
                    sellerId = 1L,
                    name = "상품1",
                    description = "설명1",
                    price = 10000L,
                    status = ProductStatus.AVAILABLE,
                    imageUrl = "https://example.com/image1.jpg",
                    createdAt = LocalDateTime.now(),
                    updatedAt = null
                ),
                ProductResponse(
                    id = 2L,
                    sellerId = 2L,
                    name = "상품2",
                    description = "설명2",
                    price = 20000L,
                    status = ProductStatus.AVAILABLE,
                    imageUrl = "https://example.com/image2.jpg",
                    createdAt = LocalDateTime.now(),
                    updatedAt = null
                )
            )
            val productResponsePage = PageImpl(productResponses, pageable, productResponses.size.toLong())

            whenever(productQueryService.getProducts(any())).thenReturn(productResponsePage)

            val response: ApiResponse<Page<ProductResponse>> = controller.getProducts(pageable)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(productResponsePage, response.data)
            verify(productQueryService).getProducts(pageable)
        }
    }

    @Nested
    @DisplayName("getProduct")
    inner class GetProduct {
        @Test
        fun 상품_상세_조회_성공() {
            val productId = 1L
            val productResponse = ProductResponse(
                id = productId,
                sellerId = 1L,
                name = "상품1",
                description = "설명1",
                price = 10000L,
                status = ProductStatus.AVAILABLE,
                imageUrl = "https://example.com/image.jpg",
                createdAt = LocalDateTime.now(),
                updatedAt = null
            )

            whenever(productQueryService.getProductById(productId)).thenReturn(productResponse)

            val response: ApiResponse<ProductResponse> = controller.getProduct(productId)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(productResponse, response.data)
            verify(productQueryService).getProductById(productId)
        }

        @Test
        fun 존재하지_않는_상품_조회_시_예외_발생() {
            val productId = 999L

            whenever(productQueryService.getProductById(productId))
                .thenThrow(com.hoppingmall.mall.product.exception.ProductNotFoundException())

            val exception = assertThrows<com.hoppingmall.mall.product.exception.ProductNotFoundException> {
                controller.getProduct(productId)
            }

            assertEquals("상품을 찾을 수 없습니다.", exception.message)
            verify(productQueryService).getProductById(productId)
        }
    }

    @Nested
    @DisplayName("getProductsBySeller")
    inner class GetProductsBySeller {
        @Test
        fun 판매자별_상품_목록_조회_성공() {
            val sellerId = 1L
            val pageable = PageRequest.of(0, 10)
            val productResponses = listOf(
                ProductResponse(
                    id = 1L,
                    sellerId = sellerId,
                    name = "상품1",
                    description = "설명1",
                    price = 10000L,
                    status = ProductStatus.AVAILABLE,
                    imageUrl = "https://example.com/image1.jpg",
                    createdAt = LocalDateTime.now(),
                    updatedAt = null
                ),
                ProductResponse(
                    id = 2L,
                    sellerId = sellerId,
                    name = "상품2",
                    description = "설명2",
                    price = 20000L,
                    status = ProductStatus.AVAILABLE,
                    imageUrl = "https://example.com/image2.jpg",
                    createdAt = LocalDateTime.now(),
                    updatedAt = null
                )
            )
            val productResponsePage = PageImpl(productResponses, pageable, productResponses.size.toLong())

            whenever(productQueryService.getProductsBySellerId(eq(sellerId), any())).thenReturn(productResponsePage)

            val response: ApiResponse<Page<ProductResponse>> = controller.getProductsBySeller(sellerId, pageable)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(productResponsePage, response.data)
            verify(productQueryService).getProductsBySellerId(sellerId, pageable)
        }
    }
} 