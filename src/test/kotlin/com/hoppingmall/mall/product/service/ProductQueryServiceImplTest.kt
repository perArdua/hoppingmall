package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.ProductImage
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@DisplayName("ProductQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductQueryServiceImplTest {

    private val productRepository: ProductRepository = mock()
    private val productImageRepository: ProductImageRepository = mock()
    private val productQueryService = ProductQueryServiceImpl(productRepository, productImageRepository)

    @Nested
    @DisplayName("getProducts")
    inner class GetProducts {
        @Test
        fun 상품_목록_조회_성공() {
            val pageable = PageRequest.of(0, 10)
            val products = listOf(
                Product.create(1L, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE).withId(1L),
                Product.create(2L, "상품2", "설명2", 20000L, ProductStatus.AVAILABLE).withId(2L)
            )
            val productPage = PageImpl(products, pageable, products.size.toLong())

            whenever(productRepository.findAll(pageable)).thenReturn(productPage)
            whenever(productImageRepository.findByProductId(1L)).thenReturn(ProductImage.create(1L, "https://example.com/image1.jpg"))
            whenever(productImageRepository.findByProductId(2L)).thenReturn(ProductImage.create(2L, "https://example.com/image2.jpg"))

            val result = productQueryService.getProducts(pageable)

            assertEquals(2, result.content.size)
            assertEquals("상품1", result.content[0].name)
            assertEquals("https://example.com/image1.jpg", result.content[0].imageUrl)
            assertEquals("상품2", result.content[1].name)
            assertEquals("https://example.com/image2.jpg", result.content[1].imageUrl)
            verify(productRepository).findAll(pageable)
        }
    }

    @Nested
    @DisplayName("getProductById")
    inner class GetProductById {
        @Test
        fun 상품_상세_조회_성공() {
            val productId = 1L
            val product = Product.create(1L, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE).withId(productId)
            val image = ProductImage.create(productId, "https://example.com/image.jpg")

            whenever(productRepository.findNullableById(productId)).thenReturn(product)
            whenever(productImageRepository.findByProductId(productId)).thenReturn(image)

            val result = productQueryService.getProductById(productId)

            assertEquals(productId, result.id)
            assertEquals(product.name, result.name)
            assertEquals(image.imageUrl, result.imageUrl)
            verify(productRepository).findNullableById(productId)
            verify(productImageRepository).findByProductId(productId)
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

        @Test
        fun 이미지가_없는_상품_조회_성공() {
            val productId = 1L
            val product = Product.create(1L, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE).withId(productId)

            whenever(productRepository.findNullableById(productId)).thenReturn(product)
            whenever(productImageRepository.findByProductId(productId)).thenReturn(null)

            val result = productQueryService.getProductById(productId)

            assertEquals(productId, result.id)
            assertEquals(product.name, result.name)
            assertEquals(null, result.imageUrl)
            verify(productRepository).findNullableById(productId)
            verify(productImageRepository).findByProductId(productId)
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
                Product.create(sellerId, "상품1", "설명1", 10000L, ProductStatus.AVAILABLE).withId(1L),
                Product.create(sellerId, "상품2", "설명2", 20000L, ProductStatus.AVAILABLE).withId(2L)
            )
            val productPage = PageImpl(products, pageable, products.size.toLong())

            whenever(productRepository.findBySellerId(sellerId, pageable)).thenReturn(productPage)
            whenever(productImageRepository.findByProductId(1L)).thenReturn(ProductImage.create(1L, "https://example.com/image1.jpg"))
            whenever(productImageRepository.findByProductId(2L)).thenReturn(ProductImage.create(2L, "https://example.com/image2.jpg"))

            val result = productQueryService.getProductsBySellerId(sellerId, pageable)

            assertEquals(2, result.content.size)
            assertEquals(sellerId, result.content[0].sellerId)
            assertEquals("상품1", result.content[0].name)
            assertEquals("https://example.com/image1.jpg", result.content[0].imageUrl)
            assertEquals(sellerId, result.content[1].sellerId)
            assertEquals("상품2", result.content[1].name)
            assertEquals("https://example.com/image2.jpg", result.content[1].imageUrl)
            verify(productRepository).findBySellerId(sellerId, pageable)
        }
    }
} 