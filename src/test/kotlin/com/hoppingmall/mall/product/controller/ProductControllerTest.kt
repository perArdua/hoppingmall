package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.service.ProductQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

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
            val products = listOf(
                Product.create(1L, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE),
                Product.create(2L, "상품2", "설명2", 20000L, ProductStatus.AVAILABLE)
            )
            val productPage = PageImpl(products, pageable, products.size.toLong())

            whenever(productQueryService.getProducts(pageable)).thenReturn(productPage)

            val response: ApiResponse<Page<Product>> = controller.getProducts(pageable)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(productPage, response.data)
            verify(productQueryService).getProducts(pageable)
        }
    }

    @Nested
    @DisplayName("getProduct")
    inner class GetProduct {
        @Test
        fun 상품_상세_조회_성공() {
            val productId = 1L
            val product = Product.create(1L, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE)

            whenever(productQueryService.getProductById(productId)).thenReturn(product)

            val response: ApiResponse<Product> = controller.getProduct(productId)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(product, response.data)
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
            val products = listOf(
                Product.create(sellerId, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE),
                Product.create(sellerId, "상품2", "설명2", 20000L, ProductStatus.AVAILABLE)
            )
            val productPage = PageImpl(products, pageable, products.size.toLong())

            whenever(productQueryService.getProductsBySellerId(sellerId, pageable)).thenReturn(productPage)

            val response: ApiResponse<Page<Product>> = controller.getProductsBySeller(sellerId, pageable)

            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(productPage, response.data)
            verify(productQueryService).getProductsBySellerId(sellerId, pageable)
        }
    }
} 