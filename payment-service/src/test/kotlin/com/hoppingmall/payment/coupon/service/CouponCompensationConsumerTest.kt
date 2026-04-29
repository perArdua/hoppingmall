package com.hoppingmall.payment.coupon.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import com.hoppingmall.payment.coupon.infrastructure.CouponRestoreResult
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.assertj.core.api.Assertions.assertThat

@DisplayName("CouponCompensationConsumer")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponCompensationConsumerTest {

    @Mock
    private lateinit var processor: CouponCompensationProcessor

    @Mock
    private lateinit var publisher: CouponCompensationPublisher

    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    private val consumer: CouponCompensationConsumer by lazy {
        CouponCompensationConsumer(processor, publisher, objectMapper)
    }

    private fun toJson(event: CouponRestoreEvent): String = objectMapper.writeValueAsString(event)

    @Test
    fun 처리_성공_시_재발행과_DLQ_모두_호출하지_않는다() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        whenever(processor.process(org.mockito.kotlin.any())).thenReturn(CouponRestoreResult.Restored)

        consumer.handle(toJson(event))

        verify(publisher, never()).publish(org.mockito.kotlin.any())
        verify(publisher, never()).publishToDlq(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun 처리_실패_시_재시도_횟수가_한계_미만이면_재발행한다() {
        val event = CouponRestoreEvent(
            couponId = 1L, userId = 10L,
            reason = CouponRestoreReason.DB_INSERT_FAILED, retryCount = 0
        )
        whenever(processor.process(org.mockito.kotlin.any())).thenThrow(RuntimeException("Redis down"))

        consumer.handle(toJson(event))

        val captor = argumentCaptor<CouponRestoreEvent>()
        verify(publisher).publish(captor.capture())
        assertThat(captor.firstValue.retryCount).isEqualTo(1)
        verify(publisher, never()).publishToDlq(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun 재시도_3회_도달_후_실패_시_DLQ로_이동하고_재발행하지_않는다() {
        val event = CouponRestoreEvent(
            couponId = 1L, userId = 10L,
            reason = CouponRestoreReason.DB_INSERT_FAILED, retryCount = 3
        )
        whenever(processor.process(org.mockito.kotlin.any())).thenThrow(RuntimeException("Redis still down"))

        consumer.handle(toJson(event))

        val captor = argumentCaptor<CouponRestoreEvent>()
        verify(publisher).publishToDlq(captor.capture(), org.mockito.kotlin.eq("Redis still down"))
        assertThat(captor.firstValue.retryCount).isEqualTo(3)
        verify(publisher, never()).publish(org.mockito.kotlin.any())
    }

    @Test
    fun 동일_이벤트_중복_수신_시_AlreadyRestored가_반환되어도_재발행하지_않는다() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        whenever(processor.process(org.mockito.kotlin.any())).thenReturn(CouponRestoreResult.AlreadyRestored)

        consumer.handle(toJson(event))

        verify(publisher, never()).publish(org.mockito.kotlin.any())
        verify(publisher, never()).publishToDlq(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
