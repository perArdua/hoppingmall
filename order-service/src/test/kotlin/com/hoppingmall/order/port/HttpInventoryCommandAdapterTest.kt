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
import org.mockito.kotlin.eq
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
    fun 배치_확정_실패_시_false를_반환한다() {
        whenever(restTemplate.postForEntity(
            any<String>(), any(), eq(HttpInventoryCommandAdapter.ConfirmationResponse::class.java)
        )).thenThrow(RuntimeException("연결 실패"))

        val result = adapter.confirmReservations(listOf("rsv-1"))

        assertThat(result).isFalse()
    }

    @Test
    fun 예약_취소_실패_시_예외를_발생시킨다() {
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )).thenThrow(RuntimeException("연결 실패"))

        assertThatThrownBy { adapter.cancelReservation("rsv-1") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("예약 취소 실패")
    }

    @Test
    fun 일괄_예약_취소_실패_시_예외를_발생시킨다() {
        whenever(restTemplate.postForEntity(
            any<String>(), any(), eq(Void::class.java)
        )).thenThrow(RuntimeException("연결 실패"))

        assertThatThrownBy { adapter.cancelReservations(listOf("rsv-1", "rsv-2")) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("예약 일괄 취소 실패")
    }
}
