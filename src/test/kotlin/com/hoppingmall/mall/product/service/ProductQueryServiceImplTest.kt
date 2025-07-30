package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

@DisplayName("ProductQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductQueryServiceImplTest {

    private val productRepository: ProductRepository = mock()
    private val productQueryService = ProductQueryServiceImpl(productRepository)

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

            whenever(productRepository.findAll(pageable)).thenReturn(productPage)

            val result = productQueryService.getProducts(pageable)

            assertEquals(productPage, result)
            verify(productRepository).findAll(pageable)
        }
    }

    @Nested
    @DisplayName("getProductById")
    inner class GetProductById {
        @Test
        fun 상품_상세_조회_성공() {
            val productId = 1L
            val product = Product.create(1L, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE)

            whenever(productRepository.findNullableById(productId)).thenReturn(product)

            val result = productQueryService.getProductById(productId)

            assertEquals(product, result)
            verify(productRepository).findNullableById(productId)
        }

        @Test
        fun 존재하지_않는_상품_ID로_조회_시_예외_발생() {
            val productId = 999L

            whenever(productRepository.findNullableById(productId)).thenReturn(null)

            assertThrows(ProductNotFoundException::class.java) {
                productQueryService.getProductById(productId)
            }

            verify(productRepository).findNullableById(productId)
        }
    }

    @Nested
    @DisplayName("getProductsBySellerId")
    inner class GetProductsBySellerId {
        @Test
        fun 판매자별_상품_목록_조회_성공() {
            val sellerId = 1L
            val pageable = PageRequest.of(0, 10)
            val products = listOf(
                Product.create(sellerId, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE),
                Product.create(sellerId, "상품2", "설명2", 20000L, ProductStatus.AVAILABLE)
            )
            val productPage = PageImpl(products, pageable, products.size.toLong())

            whenever(productRepository.findBySellerId(sellerId, pageable)).thenReturn(productPage)

            val result = productQueryService.getProductsBySellerId(sellerId, pageable)

            assertEquals(productPage, result)
            verify(productRepository).findBySellerId(sellerId, pageable)
        }
    }
} 