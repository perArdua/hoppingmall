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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.ResponseEntity
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.RestTemplate

@DisplayName("HttpInventoryCommandAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpInventoryCommandAdapterTest {

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
    fun 재고_예약_성공_시_reservationId를_반환한다() {
        val response = ResponseEntity.ok(HttpInventoryCommandAdapter.ReservationResponse("rsv-123"))
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(HttpInventoryCommandAdapter.ReservationResponse::class.java)
        )).thenReturn(response)

        val result = adapter.reserveStock(1L, 5)

        assertThat(result).isEqualTo("rsv-123")
    }

    @Test
    fun 재고_예약_응답_없음_시_예외를_발생시킨다() {
        val response = ResponseEntity.ok<HttpInventoryCommandAdapter.ReservationResponse>(null)
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(HttpInventoryCommandAdapter.ReservationResponse::class.java)
        )).thenReturn(response)

        assertThatThrownBy { adapter.reserveStock(1L, 5) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("재고 예약 응답 없음")
    }

    @Test
    fun 배치_확정_성공_시_true를_반환한다() {
        val response = ResponseEntity.ok(HttpInventoryCommandAdapter.ConfirmationResponse(true))
        whenever(restTemplate.postForEntity(
            any<String>(), any(), eq(HttpInventoryCommandAdapter.ConfirmationResponse::class.java)
        )).thenReturn(response)

        val result = adapter.confirmReservations(listOf("rsv-1", "rsv-2"))

        assertThat(result).isTrue()
    }

    @Test
    fun 배치_확정_실패_시_예외를_발생시킨다() {
        whenever(restTemplate.postForEntity(
            any<String>(), any(), eq(HttpInventoryCommandAdapter.ConfirmationResponse::class.java)
        )).thenThrow(RuntimeException("연결 실패"))

        assertThatThrownBy { adapter.confirmReservations(listOf("rsv-1")) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun 예약_취소_실패_시_예외를_발생시킨다() {
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )).thenThrow(RuntimeException("연결 실패"))

        assertThatThrownBy { adapter.cancelReservation("rsv-1") }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun 일괄_예약_취소_실패_시_예외를_발생시킨다() {
        whenever(restTemplate.postForEntity(
            any<String>(), any(), eq(Void::class.java)
        )).thenThrow(RuntimeException("연결 실패"))

        assertThatThrownBy { adapter.cancelReservations(listOf("rsv-1", "rsv-2")) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun 재고_감소를_수행한다() {
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )).thenReturn(ResponseEntity.ok().build())

        adapter.decreaseStock(1L, 5)

        org.mockito.kotlin.verify(restTemplate).postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )
    }

    @Test
    fun 재고_증가를_수행한다() {
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )).thenReturn(ResponseEntity.ok().build())

        adapter.increaseStock(1L, 5)

        org.mockito.kotlin.verify(restTemplate).postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )
    }

    @Test
    fun 배치_재고_예약을_수행한다() {
        val responseBody = mapOf(100L to "rsv-100", 200L to "rsv-200")
        val responseEntity = ResponseEntity.ok(responseBody)

        whenever(restTemplate.exchange(
            any<String>(),
            any<org.springframework.http.HttpMethod>(),
            any<org.springframework.http.HttpEntity<*>>(),
            any<org.springframework.core.ParameterizedTypeReference<Map<Long, String>>>()
        )).thenReturn(responseEntity)

        val result = adapter.batchReserveStock(listOf(100L to 1, 200L to 2))

        assertThat(result).hasSize(2)
        assertThat(result[100L]).isEqualTo("rsv-100")
        assertThat(result[200L]).isEqualTo("rsv-200")
    }

    @Test
    fun 배치_재고_예약_응답이_null이면_예외를_발생시킨다() {
        val responseEntity = ResponseEntity.ok<Map<Long, String>>(null)

        whenever(restTemplate.exchange(
            any<String>(),
            any<org.springframework.http.HttpMethod>(),
            any<org.springframework.http.HttpEntity<*>>(),
            any<org.springframework.core.ParameterizedTypeReference<Map<Long, String>>>()
        )).thenReturn(responseEntity)

        assertThatThrownBy { adapter.batchReserveStock(listOf(100L to 1)) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("배치 재고 예약 응답 없음")
    }

    @Test
    fun 예산_헤더_플래그가_켜지면_배치_재고_예약_요청에_예산_헤더를_붙인다() {
        ReflectionTestUtils.setField(adapter, "budgetHeaderEnabled", true)
        stubBatchReserve()

        adapter.batchReserveStock(listOf(100L to 1))

        val headers = capturedBatchReserveRequest().headers
        assertThat(headers.getFirst("X-Reserve-Budget-Ms")).isEqualTo("5000")
        assertThat(HttpInventoryCommandAdapter.RESERVE_BUDGET_HEADER).isEqualTo("X-Reserve-Budget-Ms")
        assertThat(HttpInventoryCommandAdapter.RESERVE_BUDGET_MS).isEqualTo(5000L)
    }

    @Test
    fun 예산_헤더_플래그가_꺼져_있으면_배치_재고_예약_요청에_예산_헤더를_붙이지_않는다() {
        stubBatchReserve()

        adapter.batchReserveStock(listOf(100L to 1))

        val request = capturedBatchReserveRequest()
        assertThat(request.headers.containsKey("X-Reserve-Budget-Ms")).isFalse()
        assertThat(request.body).isEqualTo(listOf(HttpInventoryCommandAdapter.BatchReserveRequest(100L, 1)))
    }

    @Test
    fun 예산_헤더_플래그가_켜져도_확정과_취소와_단건_예약_요청에는_헤더를_붙이지_않는다() {
        ReflectionTestUtils.setField(adapter, "budgetHeaderEnabled", true)
        whenever(restTemplate.postForEntity(any<String>(), any(), eq(HttpInventoryCommandAdapter.ConfirmationResponse::class.java)))
            .thenReturn(ResponseEntity.ok(HttpInventoryCommandAdapter.ConfirmationResponse(true)))
        whenever(restTemplate.postForEntity(any<String>(), eq(null), eq(HttpInventoryCommandAdapter.ReservationResponse::class.java)))
            .thenReturn(ResponseEntity.ok(HttpInventoryCommandAdapter.ReservationResponse("rsv-1")))
        whenever(restTemplate.postForEntity(any<String>(), anyOrNull(), eq(Void::class.java)))
            .thenReturn(ResponseEntity.ok().build())

        adapter.confirmReservations(listOf("rsv-1"))
        adapter.cancelReservations(listOf("rsv-1"))
        adapter.cancelReservation("rsv-1")
        adapter.reserveStock(1L, 1)

        val bodies = argumentCaptor<Any>()
        verify(restTemplate, times(4)).postForEntity(any<String>(), bodies.capture(), any<Class<*>>())
        assertThat(bodies.allValues).noneMatch { it is org.springframework.http.HttpEntity<*> }
        verify(restTemplate, never()).exchange(
            any<String>(),
            any<org.springframework.http.HttpMethod>(),
            any<org.springframework.http.HttpEntity<*>>(),
            any<org.springframework.core.ParameterizedTypeReference<Map<Long, String>>>()
        )
    }

    private fun stubBatchReserve() {
        whenever(restTemplate.exchange(
            any<String>(),
            any<org.springframework.http.HttpMethod>(),
            any<org.springframework.http.HttpEntity<*>>(),
            any<org.springframework.core.ParameterizedTypeReference<Map<Long, String>>>()
        )).thenReturn(ResponseEntity.ok(mapOf(100L to "rsv-100")))
    }

    private fun capturedBatchReserveRequest(): org.springframework.http.HttpEntity<*> {
        val request = argumentCaptor<org.springframework.http.HttpEntity<*>>()
        verify(restTemplate).exchange(
            eq("http://localhost:8083/internal/api/v1/inventory/batch-reserve"),
            eq(org.springframework.http.HttpMethod.POST),
            request.capture(),
            any<org.springframework.core.ParameterizedTypeReference<Map<Long, String>>>()
        )
        return request.firstValue
    }

    @Test
    fun 예약_취소를_수행한다() {
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )).thenReturn(ResponseEntity.ok().build())

        adapter.cancelReservation("rsv-1")

        org.mockito.kotlin.verify(restTemplate).postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )
    }

    @Test
    fun 일괄_예약_취소를_수행한다() {
        whenever(restTemplate.postForEntity(
            any<String>(), any(), eq(Void::class.java)
        )).thenReturn(ResponseEntity.ok().build())

        adapter.cancelReservations(listOf("rsv-1", "rsv-2"))

        org.mockito.kotlin.verify(restTemplate).postForEntity(
            any<String>(), any(), eq(Void::class.java)
        )
    }

    @Test
    fun 배치_확정_응답이_null이면_false를_반환한다() {
        val response = ResponseEntity.ok<HttpInventoryCommandAdapter.ConfirmationResponse>(null)
        whenever(restTemplate.postForEntity(
            any<String>(), any(), eq(HttpInventoryCommandAdapter.ConfirmationResponse::class.java)
        )).thenReturn(response)

        val result = adapter.confirmReservations(listOf("rsv-1"))

        assertThat(result).isFalse()
    }
}
