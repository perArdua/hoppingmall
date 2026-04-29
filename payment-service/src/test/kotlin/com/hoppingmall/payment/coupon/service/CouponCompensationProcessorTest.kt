package com.hoppingmall.payment.coupon.service

import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import com.hoppingmall.payment.coupon.infrastructure.CouponRestoreResult
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("CouponCompensationProcessor")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponCompensationProcessorTest {

    @Mock
    private lateinit var couponStockRedisRepository: CouponStockRedisRepository

    @Mock
    private lateinit var metrics: CouponCompensationMetrics

    @InjectMocks
    private lateinit var processor: CouponCompensationProcessor

    @Test
    fun 보상_복원_성공_시_Restored_반환_및_consumed_메트릭_기록() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        whenever(couponStockRedisRepository.restoreStockIdempotent(1L, 10L))
            .thenReturn(CouponRestoreResult.Restored)

        val result = processor.process(event)

        assertThat(result).isEqualTo(CouponRestoreResult.Restored)
        verify(metrics).recordAsyncConsumed()
    }

    @Test
    fun 이미_복원된_경우_AlreadyRestored_반환하고_consumed_메트릭은_여전히_기록한다() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        whenever(couponStockRedisRepository.restoreStockIdempotent(1L, 10L))
            .thenReturn(CouponRestoreResult.AlreadyRestored)

        val result = processor.process(event)

        assertThat(result).isEqualTo(CouponRestoreResult.AlreadyRestored)
        verify(metrics).recordAsyncConsumed()
    }

    @Test
    fun Redis_저장소_예외_발생_시_상위로_전파한다() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        whenever(couponStockRedisRepository.restoreStockIdempotent(1L, 10L))
            .thenThrow(RuntimeException("Redis down"))

        assertThatThrownBy { processor.process(event) }
            .isInstanceOf(RuntimeException::class.java)
        verify(metrics, never()).recordAsyncConsumed()
    }
}
