package com.hoppingmall.product.product.controller

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductSearchCondition
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.dto.response.ProductImageResponse
import com.hoppingmall.product.product.dto.response.ProductResponse
import com.hoppingmall.product.product.exception.ProductNotFoundException
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.product.service.ProductImageService
import com.hoppingmall.product.product.service.ProductQueryService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("ProductController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductControllerTest {

    @Mock
    private lateinit var productQueryService: ProductQueryService

    @Mock
    private lateinit var productCommandService: ProductCommandService

    @Mock
    private lateinit var productImageService: ProductImageService

    @InjectMocks
    private lateinit var controller: ProductController

    private fun productResponse() = ProductResponse(
        id = 1L, sellerId = 1L, categoryId = 1L, name = "테스트",
        description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE,
        imageUrls = emptyList(), createdAt = LocalDateTime.now(), updatedAt = null
    )

    @Test
    fun 상품_목록을_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(productQueryService.getProducts(pageable))
            .thenReturn(SliceImpl(listOf(productResponse()), pageable, false))

        val result = controller.getProducts(pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 상품을_단건_조회한다() {
        whenever(productQueryService.getProductById(1L)).thenReturn(productResponse())

        val result = controller.getProduct(1L)

        assertThat(result.data!!.name).isEqualTo("테스트")
    }

    @Test
    fun 존재하지_않는_상품_조회_시_예외를_발생시킨다() {
        whenever(productQueryService.getProductById(999L)).thenReturn(null)

        assertThatThrownBy { controller.getProduct(999L) }
            .isInstanceOf(ProductNotFoundException::class.java)
    }

    @Test
    fun 판매자별_상품을_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(productQueryService.getProductsBySellerId(eq(1L), eq(pageable)))
            .thenReturn(SliceImpl(listOf(productResponse()), pageable, false))

        val result = controller.getProductsBySeller(1L, pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 카테고리별_상품을_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(productQueryService.getProductsByCategoryId(eq(1L), eq(pageable)))
            .thenReturn(SliceImpl(listOf(productResponse()), pageable, false))

        val result = controller.getProductsByCategory(1L, pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 상품을_검색한다() {
        val pageable = PageRequest.of(0, 10)
        val condition = ProductSearchCondition(keyword = "테스트")

        whenever(productQueryService.searchProducts(any(), eq(pageable)))
            .thenReturn(SliceImpl(listOf(productResponse()), pageable, false))

        val result = controller.searchProducts(condition, pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 상품을_생성한다() {
        val request = ProductCreateRequest(
            sellerId = 1L, categoryId = 1L, name = "테스트",
            description = "설명", price = BigDecimal("10000")
        )

        whenever(productCommandService.createProduct(any())).thenReturn(productResponse())

        val result = controller.createProduct(request)

        assertThat(result.data!!.name).isEqualTo("테스트")
    }

    @Test
    fun 상품을_수정한다() {
        val request = ProductUpdateRequest(
            categoryId = 1L, name = "수정", description = "수정", price = BigDecimal("20000")
        )

        whenever(productCommandService.updateProduct(eq(1L), any())).thenReturn(productResponse())

        val result = controller.updateProduct(1L, request)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 상품을_삭제한다() {
        val result = controller.deleteProduct(1L)

        verify(productCommandService).deleteProduct(1L)
        assertThat(result.data).isNotNull
    }

    @Test
    fun 상품_이미지를_업로드한다() {
        val file = MockMultipartFile("image", "test.jpg", "image/jpeg", "test".toByteArray())
        val imageResponse = ProductImageResponse("http://img.jpg", "test.jpg", 4L)

        whenever(productImageService.uploadProductImage(any())).thenReturn(imageResponse)

        val result = controller.uploadProductImage(file)

        assertThat(result.data!!.imageUrl).isEqualTo("http://img.jpg")
    }
}
