package com.hoppingmall.order.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

@DisplayName("HttpProductQueryAdapter")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpProductQueryAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var adapter: HttpProductQueryAdapter

    @BeforeEach
    fun setUp() {
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpProductQueryAdapter("http://localhost:8083", restTemplateBuilder)
    }

    @Test
    fun 상품ID로_상품정보를_조회한다() {
        val productInfo = ProductInfo(
            id = 100L, name = "테스트 상품",
            price = BigDecimal("10000"), sellerId = 5L, imageUrl = "http://example.com/img.jpg"
        )
        whenever(restTemplate.getForObject(
            any<String>(), eq(ProductInfo::class.java)
        )).thenReturn(productInfo)

        val result = adapter.findProductById(100L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(100L)
        assertThat(result.name).isEqualTo("테스트 상품")
    }

    @Test
    fun 상품이_없으면_null을_반환한다() {
        whenever(restTemplate.getForObject(
            any<String>(), eq(ProductInfo::class.java)
        )).thenReturn(null)

        val result = adapter.findProductById(999L)

        assertThat(result).isNull()
    }

    @Test
    fun 상품ID_목록으로_상품정보를_조회한다() {
        val products = arrayOf(
            ProductInfo(id = 1L, name = "상품A", price = BigDecimal("10000"), sellerId = 5L),
            ProductInfo(id = 2L, name = "상품B", price = BigDecimal("20000"), sellerId = 6L)
        )
        whenever(restTemplate.getForObject(
            any<String>(), eq(Array<ProductInfo>::class.java)
        )).thenReturn(products)

        val result = adapter.findProductsByIds(listOf(1L, 2L))

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[1].id).isEqualTo(2L)
    }

    @Test
    fun 상품_목록_조회결과가_null이면_빈_목록을_반환한다() {
        whenever(restTemplate.getForObject(
            any<String>(), eq(Array<ProductInfo>::class.java)
        )).thenReturn(null)

        val result = adapter.findProductsByIds(listOf(1L, 2L))

        assertThat(result).isEmpty()
    }

    @Test
    fun 단건조회_CB_폴백_시_null을_반환한다() {
        val method = HttpProductQueryAdapter::class.java.getDeclaredMethod(
            "findProductByIdFallback", Long::class.java, Exception::class.java
        )
        method.isAccessible = true

        val result = method.invoke(adapter, 1L, RuntimeException("fail"))

        assertThat(result).isNull()
    }

    @Test
    fun 목록조회_CB_폴백_시_빈_목록을_반환한다() {
        val method = HttpProductQueryAdapter::class.java.getDeclaredMethod(
            "findProductsByIdsFallback", List::class.java, Exception::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(adapter, listOf(1L, 2L), RuntimeException("fail")) as List<ProductInfo>

        assertThat(result).isEmpty()
    }
}
