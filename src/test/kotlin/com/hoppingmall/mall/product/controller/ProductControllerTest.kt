package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.dto.request.ProductCreateRequest
import com.hoppingmall.mall.product.dto.request.ProductUpdateRequest
import com.hoppingmall.mall.product.dto.response.ProductImageResponse
import com.hoppingmall.mall.product.dto.response.ProductResponse
import com.hoppingmall.mall.product.service.ProductCommandService
import com.hoppingmall.mall.product.service.ProductImageService
import com.hoppingmall.mall.product.service.ProductQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.mock.web.MockMultipartFile
import java.time.LocalDateTime

@DisplayName("ProductController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductControllerTest {

    private val productQueryService: ProductQueryService = mock()
    private val productCommandService: ProductCommandService = mock()
    private val productImageService: ProductImageService = mock()
    private val controller = ProductController(productQueryService, productCommandService, productImageService)

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

    @Nested
    @DisplayName("createProduct")
    inner class CreateProduct {
        @Test
        fun 상품_생성_성공() {
            val request = ProductCreateRequest(
                sellerId = 1L,
                name = "새 상품",
                description = "새 상품 설명",
                price = 15000L,
                imageUrl = "https://example.com/new-image.jpg",
                status = ProductStatus.AVAILABLE
            )

            val expectedResponse = ProductResponse(
                id = 1L,
                sellerId = 1L,
                name = "새 상품",
                description = "새 상품 설명",
                price = 15000L,
                status = ProductStatus.AVAILABLE,
                imageUrl = "https://example.com/new-image.jpg",
                createdAt = LocalDateTime.now(),
                updatedAt = null
            )

            whenever(productCommandService.createProduct(request)).thenReturn(expectedResponse)

            val response: ApiResponse<ProductResponse> = controller.createProduct(request)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(expectedResponse, response.data)
            verify(productCommandService).createProduct(request)
        }
    }

    @Nested
    @DisplayName("updateProduct")
    inner class UpdateProduct {
        @Test
        fun 상품_수정_성공() {
            val productId = 1L
            val request = ProductUpdateRequest(
                name = "수정된 상품",
                description = "수정된 상품 설명",
                price = 20000L,
                imageUrl = "https://example.com/updated-image.jpg",
                status = ProductStatus.AVAILABLE
            )

            val expectedResponse = ProductResponse(
                id = productId,
                sellerId = 1L,
                name = "수정된 상품",
                description = "수정된 상품 설명",
                price = 20000L,
                status = ProductStatus.AVAILABLE,
                imageUrl = "https://example.com/updated-image.jpg",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            whenever(productCommandService.updateProduct(productId, request)).thenReturn(expectedResponse)

            val response: ApiResponse<ProductResponse> = controller.updateProduct(productId, request)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(expectedResponse, response.data)
            verify(productCommandService).updateProduct(productId, request)
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    inner class DeleteProduct {
        @Test
        fun 상품_삭제_성공() {
            val productId = 1L

            doNothing().whenever(productCommandService).deleteProduct(productId)

            val response: ApiResponse<Unit> = controller.deleteProduct(productId)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(Unit, response.data)
            verify(productCommandService).deleteProduct(productId)
        }
    }

    @Nested
    @DisplayName("uploadProductImage")
    inner class UploadProductImage {
        @Test
        fun 상품_이미지_업로드_성공() {
            val imageContent = "fake-image-content".toByteArray()
            val imageFile = MockMultipartFile(
                "image",
                "test-image.jpg",
                "image/jpeg",
                imageContent
            )

            val expectedResponse = ProductImageResponse(
                imageUrl = "D:/hoppingmall/product/images/uuid.jpg",
                fileName = "uuid.jpg",
                fileSize = imageContent.size.toLong()
            )

            whenever(productImageService.uploadProductImage(any())).thenReturn(expectedResponse)

            val response: ApiResponse<ProductImageResponse> = controller.uploadProductImage(imageFile)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(expectedResponse, response.data)
            verify(productImageService).uploadProductImage(imageFile)
        }
    }
} 