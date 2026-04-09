package com.hoppingmall.product.product.service

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.ProductSearchCondition
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal

@DisplayName("ProductQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductQueryServiceImplTest {

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var productImageRepository: ProductImageRepository

    @InjectMocks
    private lateinit var service: ProductQueryServiceImpl

    private fun createProduct(id: Long = 1L) = Product.create(
        sellerId = 1L, categoryId = 1L, name = "테스트상품",
        description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
    ).withId(id)

    private fun createImage(productId: Long = 1L) = ProductImage.create(
        productId = productId, imageUrl = "http://img.jpg", sortOrder = 0
    ).withId(1L)

    @Test
    fun 전체_상품을_페이지로_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val product = createProduct()
        val slice = SliceImpl(listOf(product), pageable, false)

        whenever(productRepository.findBy(pageable)).thenReturn(slice)
        whenever(productImageRepository.findByProductIdIn(listOf(1L))).thenReturn(listOf(createImage()))

        val result = service.getProducts(pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 상품을_ID로_조회한다() {
        val product = createProduct()

        whenever(productRepository.findNullableById(1L)).thenReturn(product)
        whenever(productImageRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(listOf(createImage()))

        val result = service.getProductById(1L)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("테스트상품")
    }

    @Test
    fun 존재하지_않는_상품_조회_시_null을_반환한다() {
        whenever(productRepository.findNullableById(999L)).thenReturn(null)

        val result = service.getProductById(999L)

        assertThat(result).isNull()
    }

    @Test
    fun 판매자_ID로_상품을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val product = createProduct()
        val slice = SliceImpl(listOf(product), pageable, false)

        whenever(productRepository.findBySellerId(1L, pageable)).thenReturn(slice)
        whenever(productImageRepository.findByProductIdIn(listOf(1L))).thenReturn(listOf(createImage()))

        val result = service.getProductsBySellerId(1L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 카테고리_ID로_상품을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val product = createProduct()
        val slice = SliceImpl(listOf(product), pageable, false)

        whenever(productRepository.findByCategoryId(1L, pageable)).thenReturn(slice)
        whenever(productImageRepository.findByProductIdIn(listOf(1L))).thenReturn(listOf(createImage()))

        val result = service.getProductsByCategoryId(1L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 검색_조건으로_상품을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val product = createProduct()
        val slice = SliceImpl(listOf(product), pageable, false)
        val condition = ProductSearchCondition(keyword = "테스트")

        whenever(
            productRepository.searchProducts(
                keyword = anyOrNull(), categoryId = anyOrNull(), status = anyOrNull(),
                minPrice = anyOrNull(), maxPrice = anyOrNull(), pageable = any()
            )
        ).thenReturn(slice)
        whenever(productImageRepository.findByProductIdIn(listOf(1L))).thenReturn(listOf(createImage()))

        val result = service.searchProducts(condition, pageable)

        assertThat(result.content).hasSize(1)
    }
}
