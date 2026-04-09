package com.hoppingmall.product.internal

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@DisplayName("InternalProductController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InternalProductControllerTest {

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var productImageRepository: ProductImageRepository

    @InjectMocks
    private lateinit var controller: InternalProductController

    @Test
    fun 상품_단건_조회_성공() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val image = ProductImage.create(productId = 1L, imageUrl = "http://img.jpg", sortOrder = 0).withId(1L)

        whenever(productRepository.findNullableById(1L)).thenReturn(product)
        whenever(productImageRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(listOf(image))

        val result = controller.getProduct(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.name).isEqualTo("테스트상품")
        assertThat(result.body!!.imageUrl).isEqualTo("http://img.jpg")
    }

    @Test
    fun 존재하지_않는_상품_조회_시_404를_반환한다() {
        whenever(productRepository.findNullableById(999L)).thenReturn(null)

        val result = controller.getProduct(999L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun 상품_목록을_ID로_조회한다() {
        val products = listOf(
            Product.create(
                sellerId = 1L, categoryId = 1L, name = "상품1",
                description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
            ).withId(1L),
            Product.create(
                sellerId = 1L, categoryId = 1L, name = "상품2",
                description = "설명", price = BigDecimal("20000"), status = ProductStatus.AVAILABLE
            ).withId(2L)
        )

        whenever(productRepository.findAllById(listOf(1L, 2L))).thenReturn(products)

        val result = controller.getProductsByIds(listOf(1L, 2L))

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body).hasSize(2)
    }

    @Test
    fun 상품_이미지_URL을_조회한다() {
        val image = ProductImage.create(productId = 1L, imageUrl = "http://img.jpg", sortOrder = 0).withId(1L)

        whenever(productImageRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(listOf(image))

        val result = controller.getProductImageUrl(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body).isEqualTo("http://img.jpg")
    }

    @Test
    fun 이미지가_없는_상품의_이미지_URL_조회_시_빈_문자열을_반환한다() {
        whenever(productImageRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(emptyList())

        val result = controller.getProductImageUrl(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body).isEmpty()
    }
}
