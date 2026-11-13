package com.hoppingmall.order.port

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.lang.reflect.InvocationTargetException

@DisplayName("HTTP 상품 조회 어댑터의 연결 실패 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpProductQueryAdapterFallbackTest {

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
    fun 단건_조회가_연결_실패면_상품_없음이_아니라_조회_불가_예외를_던진다() {
        val failure = ResourceAccessException("Connection refused")

        assertThatThrownBy { findProductByIdFallback(1L, failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
            .hasCause(failure)
    }

    @Test
    fun 서킷이_열려_단건_조회를_부르지_못하면_조회_불가_예외를_던진다() {
        val failure = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("product-query"))

        assertThatThrownBy { findProductByIdFallback(1L, failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
    }

    @Test
    fun 단건_조회가_404면_상품_없음으로_null을_돌려준다() {
        val result = findProductByIdFallback(1L, HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, ByteArray(0), null))

        assertThat(result).isNull()
    }

    @Test
    fun 목록_조회가_연결_실패면_빈_목록이_아니라_조회_불가_예외를_던진다() {
        val failure = ResourceAccessException("Connection refused")

        assertThatThrownBy { findProductsByIdsFallback(listOf(1L, 2L), failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
            .hasCause(failure)
    }

    private fun findProductByIdFallback(productId: Long, e: Exception): ProductInfo? =
        invoke("findProductByIdFallback", arrayOf(Long::class.java, Exception::class.java), productId, e) as ProductInfo?

    @Suppress("UNCHECKED_CAST")
    private fun findProductsByIdsFallback(productIds: List<Long>, e: Exception): List<ProductInfo> =
        invoke("findProductsByIdsFallback", arrayOf(List::class.java, Exception::class.java), productIds, e) as List<ProductInfo>

    private fun invoke(name: String, parameterTypes: Array<Class<*>>, vararg args: Any): Any? {
        val method = HttpProductQueryAdapter::class.java.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return try {
            method.invoke(adapter, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
