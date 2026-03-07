package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.global.common.config.cache.NotFoundMarker
import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import java.math.BigDecimal
import com.hoppingmall.mall.product.domain.ProductImage
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.dto.request.ProductSearchCondition
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl

@DisplayName("ProductQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductQueryServiceImplTest {

    private val productRepository: ProductRepository = mock()
    private val productImageRepository: ProductImageRepository = mock()
    private val cacheManager: CacheManager = mock()
    private val notFoundCache: Cache = mock()
    private val productQueryService = ProductQueryServiceImpl(productRepository, productImageRepository, cacheManager)

    @Nested
    @DisplayName("getProducts")
    inner class GetProducts {
        @Test
        fun 상품_목록_조회_성공() {
            val pageable = PageRequest.of(0, 10)
            val products = listOf(
                Product.create(1L, 1L, "상품1", "설명1", BigDecimal("10000"), ProductStatus.AVAILABLE).withId(1L),
                Product.create(2L, 1L, "상품2", "설명2", BigDecimal("20000"), ProductStatus.AVAILABLE).withId(2L)
            )
            val productSlice = SliceImpl(products, pageable, false)

            whenever(productRepository.findBy(pageable)).thenReturn(productSlice)
            whenever(productImageRepository.findByProductIdIn(listOf(1L, 2L))).thenReturn(listOf(
                ProductImage.create(1L, "https://example.com/image1.jpg"),
                ProductImage.create(2L, "https://example.com/image2.jpg")
            ))

            val result = productQueryService.getProducts(pageable)

            assertEquals(2, result.content.size)
            assertEquals("상품1", result.content[0].name)
            assertEquals("https://example.com/image1.jpg", result.content[0].imageUrl)
            assertEquals("상품2", result.content[1].name)
            assertEquals("https://example.com/image2.jpg", result.content[1].imageUrl)
            verify(productRepository).findBy(pageable)
            verify(productImageRepository).findByProductIdIn(listOf(1L, 2L))
        }
    }

    @Nested
    @DisplayName("getProductById")
    inner class GetProductById {
        @Test
        fun 상품_상세_조회_성공() {
            val productId = 1L
            val product = Product.create(1L, 1L, "상품1", "설명1", BigDecimal("10000"), ProductStatus.AVAILABLE).withId(productId)
            val image = ProductImage.create(productId, "https://example.com/image.jpg")

            whenever(cacheManager.getCache("product:notfound")).thenReturn(notFoundCache)
            whenever(notFoundCache.get(productId)).thenReturn(null)
            whenever(productRepository.findNullableById(productId)).thenReturn(product)
            whenever(productImageRepository.findByProductId(productId)).thenReturn(image)

            val result = productQueryService.getProductById(productId)

            assertEquals(productId, result!!.id)
            assertEquals(product.name, result.name)
            assertEquals(image.imageUrl, result.imageUrl)
            verify(productRepository).findNullableById(productId)
            verify(productImageRepository).findByProductId(productId)
        }

        @Test
        fun 존재하지_않는_상품_ID로_조회_시_null을_반환하고_notfound_캐시에_마커를_저장한다() {
            val productId = 999L

            whenever(cacheManager.getCache("product:notfound")).thenReturn(notFoundCache)
            whenever(notFoundCache.get(productId)).thenReturn(null)
            whenever(productRepository.findNullableById(productId)).thenReturn(null)

            val result = productQueryService.getProductById(productId)

            assertNull(result)
            verify(productRepository).findNullableById(productId)
            verify(notFoundCache).put(productId, NotFoundMarker.INSTANCE)
        }

        @Test
        fun notfound_캐시에_마커가_있으면_DB를_조회하지_않고_null을_반환한다() {
            val productId = 999L
            val markerWrapper: Cache.ValueWrapper = mock()

            whenever(cacheManager.getCache("product:notfound")).thenReturn(notFoundCache)
            whenever(notFoundCache.get(productId)).thenReturn(markerWrapper)
            whenever(markerWrapper.get()).thenReturn(NotFoundMarker.INSTANCE)

            val result = productQueryService.getProductById(productId)

            assertNull(result)
            verify(productRepository, never()).findNullableById(any())
        }

        @Test
        fun 이미지가_없는_상품_조회_성공() {
            val productId = 1L
            val product = Product.create(1L, 1L, "상품1", "설명1", BigDecimal("10000"), ProductStatus.AVAILABLE).withId(productId)

            whenever(cacheManager.getCache("product:notfound")).thenReturn(notFoundCache)
            whenever(notFoundCache.get(productId)).thenReturn(null)
            whenever(productRepository.findNullableById(productId)).thenReturn(product)
            whenever(productImageRepository.findByProductId(productId)).thenReturn(null)

            val result = productQueryService.getProductById(productId)

            assertEquals(productId, result!!.id)
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
                Product.create(sellerId, 1L, "상품1", "설명1", BigDecimal("10000"), ProductStatus.AVAILABLE).withId(1L),
                Product.create(sellerId, 1L, "상품2", "설명2", BigDecimal("20000"), ProductStatus.AVAILABLE).withId(2L)
            )
            val productSlice = SliceImpl(products, pageable, false)

            whenever(productRepository.findBySellerId(sellerId, pageable)).thenReturn(productSlice)
            whenever(productImageRepository.findByProductIdIn(listOf(1L, 2L))).thenReturn(listOf(
                ProductImage.create(1L, "https://example.com/image1.jpg"),
                ProductImage.create(2L, "https://example.com/image2.jpg")
            ))

            val result = productQueryService.getProductsBySellerId(sellerId, pageable)

            assertEquals(2, result.content.size)
            assertEquals(sellerId, result.content[0].sellerId)
            assertEquals("상품1", result.content[0].name)
            assertEquals("https://example.com/image1.jpg", result.content[0].imageUrl)
            assertEquals(sellerId, result.content[1].sellerId)
            assertEquals("상품2", result.content[1].name)
            assertEquals("https://example.com/image2.jpg", result.content[1].imageUrl)
            verify(productRepository).findBySellerId(sellerId, pageable)
            verify(productImageRepository).findByProductIdIn(listOf(1L, 2L))
        }
    }

    @Nested
    @DisplayName("getProductsByCategoryId")
    inner class GetProductsByCategoryId {
        @Test
        fun 카테고리별_상품_목록_조회_성공() {
            val categoryId = 1L
            val pageable = PageRequest.of(0, 10)
            val products = listOf(
                Product.create(1L, categoryId, "상품1", "설명1", BigDecimal("10000"), ProductStatus.AVAILABLE).withId(1L),
                Product.create(2L, categoryId, "상품2", "설명2", BigDecimal("20000"), ProductStatus.AVAILABLE).withId(2L)
            )
            val productSlice = SliceImpl(products, pageable, false)

            whenever(productRepository.findByCategoryId(categoryId, pageable)).thenReturn(productSlice)
            whenever(productImageRepository.findByProductIdIn(listOf(1L, 2L))).thenReturn(listOf(
                ProductImage.create(1L, "https://example.com/image1.jpg"),
                ProductImage.create(2L, "https://example.com/image2.jpg")
            ))

            val result = productQueryService.getProductsByCategoryId(categoryId, pageable)

            assertEquals(2, result.content.size)
            assertEquals(categoryId, result.content[0].categoryId)
            assertEquals("상품1", result.content[0].name)
            assertEquals("https://example.com/image1.jpg", result.content[0].imageUrl)
            assertEquals(categoryId, result.content[1].categoryId)
            assertEquals("상품2", result.content[1].name)
            assertEquals("https://example.com/image2.jpg", result.content[1].imageUrl)
            verify(productRepository).findByCategoryId(categoryId, pageable)
            verify(productImageRepository).findByProductIdIn(listOf(1L, 2L))
        }
    }

    @Nested
    @DisplayName("searchProducts")
    inner class SearchProducts {
        @Test
        fun 키워드로_상품_검색_성공() {
            val pageable = PageRequest.of(0, 10)
            val condition = ProductSearchCondition(keyword = "노트북")
            val products = listOf(
                Product.create(1L, 1L, "게이밍 노트북", "설명1", BigDecimal("1500000"), ProductStatus.AVAILABLE).withId(1L)
            )
            val productSlice = SliceImpl(products, pageable, false)

            whenever(productRepository.searchProducts(
                eq("노트북"), isNull(), isNull(), isNull(), isNull(), eq(pageable)
            )).thenReturn(productSlice)
            whenever(productImageRepository.findByProductIdIn(listOf(1L))).thenReturn(emptyList())

            val result = productQueryService.searchProducts(condition, pageable)

            assertEquals(1, result.content.size)
            assertEquals("게이밍 노트북", result.content[0].name)
        }

        @Test
        fun 가격_범위로_상품_검색_성공() {
            val pageable = PageRequest.of(0, 10)
            val condition = ProductSearchCondition(
                minPrice = BigDecimal("10000"),
                maxPrice = BigDecimal("30000")
            )
            val products = listOf(
                Product.create(1L, 1L, "상품1", "설명1", BigDecimal("15000"), ProductStatus.AVAILABLE).withId(1L),
                Product.create(2L, 1L, "상품2", "설명2", BigDecimal("25000"), ProductStatus.AVAILABLE).withId(2L)
            )
            val productSlice = SliceImpl(products, pageable, false)

            whenever(productRepository.searchProducts(
                isNull(), isNull(), isNull(), eq(BigDecimal("10000")), eq(BigDecimal("30000")), eq(pageable)
            )).thenReturn(productSlice)
            whenever(productImageRepository.findByProductIdIn(listOf(1L, 2L))).thenReturn(emptyList())

            val result = productQueryService.searchProducts(condition, pageable)

            assertEquals(2, result.content.size)
        }

        @Test
        fun 복합_조건으로_상품_검색_성공() {
            val pageable = PageRequest.of(0, 10)
            val condition = ProductSearchCondition(
                keyword = "상품",
                categoryId = 1L,
                status = ProductStatus.AVAILABLE,
                minPrice = BigDecimal("5000"),
                maxPrice = BigDecimal("50000")
            )
            val products = listOf(
                Product.create(1L, 1L, "상품1", "설명1", BigDecimal("10000"), ProductStatus.AVAILABLE).withId(1L)
            )
            val productSlice = SliceImpl(products, pageable, false)

            whenever(productRepository.searchProducts(
                eq("상품"), eq(1L), eq(ProductStatus.AVAILABLE),
                eq(BigDecimal("5000")), eq(BigDecimal("50000")), eq(pageable)
            )).thenReturn(productSlice)
            whenever(productImageRepository.findByProductIdIn(listOf(1L))).thenReturn(listOf(
                ProductImage.create(1L, "https://example.com/image1.jpg")
            ))

            val result = productQueryService.searchProducts(condition, pageable)

            assertEquals(1, result.content.size)
            assertEquals("상품1", result.content[0].name)
            assertEquals("https://example.com/image1.jpg", result.content[0].imageUrl)
        }

        @Test
        fun 검색_결과가_없으면_빈_슬라이스_반환() {
            val pageable = PageRequest.of(0, 10)
            val condition = ProductSearchCondition(keyword = "존재하지않는상품")
            val productSlice = SliceImpl<Product>(emptyList(), pageable, false)

            whenever(productRepository.searchProducts(
                eq("존재하지않는상품"), isNull(), isNull(), isNull(), isNull(), eq(pageable)
            )).thenReturn(productSlice)

            val result = productQueryService.searchProducts(condition, pageable)

            assertEquals(0, result.content.size)
            assertEquals(false, result.hasNext())
        }
    }
}
