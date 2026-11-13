package com.hoppingmall.order.port

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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.net.SocketTimeoutException

@DisplayName("HttpInventoryCommandAdapter 예약 요청 키")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpInventoryCommandAdapterRequestKeyTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var adapter: HttpInventoryCommandAdapter

    @BeforeEach
    fun setUp() {
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpInventoryCommandAdapter("http://localhost:8083", restTemplateBuilder)
    }

    @Test
    fun 배치_예약_요청에_요청_키_헤더를_붙인다() {
        whenever(exchange()).thenReturn(ResponseEntity.ok(mapOf(100L to "rsv-100")))

        adapter.batchReserveStock(listOf(100L to 1))

        assertThat(sentKeys(1).single()).isNotBlank()
    }

    @Test
    fun 배치_예약을_부를_때마다_다른_요청_키를_보낸다() {
        whenever(exchange()).thenReturn(ResponseEntity.ok(mapOf(100L to "rsv-100")))

        adapter.batchReserveStock(listOf(100L to 1))
        adapter.batchReserveStock(listOf(100L to 1))

        val keys = sentKeys(2)
        assertThat(keys).doesNotContainNull()
        assertThat(keys.toSet()).hasSize(2)
    }

    @Test
    fun 배치_예약이_읽기_시간_초과로_끝나면_같은_요청_키로_다시_보내고_그_응답을_돌려준다() {
        whenever(exchange())
            .thenThrow(readTimeout())
            .thenReturn(ResponseEntity.ok(mapOf(100L to "rsv-100")))

        val result = adapter.batchReserveStock(listOf(100L to 1))

        assertThat(result).isEqualTo(mapOf(100L to "rsv-100"))
        val keys = sentKeys(2)
        assertThat(keys[0]).isNotBlank()
        assertThat(keys[1]).isEqualTo(keys[0])
    }

    @Test
    fun 다시_보낸_배치_예약도_읽기_시간_초과로_끝나면_예외를_그대로_던진다() {
        whenever(exchange()).thenThrow(readTimeout())

        assertThatThrownBy { adapter.batchReserveStock(listOf(100L to 1)) }
            .isInstanceOf(ResourceAccessException::class.java)
        val keys = sentKeys(2)
        assertThat(keys[1]).isEqualTo(keys[0])
    }

    @Test
    fun 상품_서비스가_재고_부족으로_거절하면_다시_보내지_않는다() {
        whenever(exchange()).thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", org.springframework.http.HttpHeaders(), ByteArray(0), null))

        assertThatThrownBy { adapter.batchReserveStock(listOf(100L to 1)) }
            .isInstanceOf(HttpClientErrorException.BadRequest::class.java)
        sentKeys(1)
    }

    private fun exchange(): ResponseEntity<Map<Long, String>>? =
        restTemplate.exchange(
            any<String>(),
            any<HttpMethod>(),
            any<HttpEntity<*>>(),
            any<ParameterizedTypeReference<Map<Long, String>>>()
        )

    private fun sentKeys(expectedCalls: Int): List<String?> {
        val requests = argumentCaptor<HttpEntity<*>>()
        verify(restTemplate, times(expectedCalls)).exchange(
            eq("http://localhost:8083/internal/api/v1/inventory/batch-reserve"),
            eq(HttpMethod.POST),
            requests.capture(),
            any<ParameterizedTypeReference<Map<Long, String>>>()
        )
        return requests.allValues.map { it.headers.getFirst(REQUEST_KEY_HEADER) }
    }

    private fun readTimeout() = ResourceAccessException("I/O error: Read timed out", SocketTimeoutException("Read timed out"))

    companion object {
        private const val REQUEST_KEY_HEADER = "X-Reservation-Request-Key"
    }
}
