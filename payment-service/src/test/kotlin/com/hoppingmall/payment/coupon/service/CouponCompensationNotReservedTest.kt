package com.hoppingmall.payment.coupon.service

import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import com.hoppingmall.payment.coupon.infrastructure.CouponRestoreResult
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.Codec

@DisplayName("쿠폰 보상 큐 복원이 발급 집합에 없는 사용자를 만났을 때")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponCompensationNotReservedTest {

    @Mock
    private lateinit var couponStockRedisRepository: CouponStockRedisRepository

    @Mock
    private lateinit var metrics: CouponCompensationMetrics

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var rScript: RScript

    @InjectMocks
    private lateinit var processor: CouponCompensationProcessor

    @Test
    fun 복원_스크립트가_3을_돌려주면_NotReserved로_본다() {
        whenever(redissonClient.getScript(any<Codec>())).thenReturn(rScript)
        whenever(rScript.eval<Long>(any(), eq(CouponStockRedisRepository.RESTORE_IDEMPOTENT_SCRIPT), any(), any<List<Any>>(), any(), any()))
            .thenReturn(3L)

        val result = CouponStockRedisRepository(redissonClient).restoreStockIdempotent(1L, 10L)

        assertThat(result).isEqualTo(CouponRestoreResult.NotReserved)
    }

    @Test
    fun 처리기는_NotReserved를_그대로_돌려주고_소비_메트릭을_기록한다() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        whenever(couponStockRedisRepository.restoreStockIdempotent(1L, 10L)).thenReturn(CouponRestoreResult.NotReserved)

        val result = processor.process(event)

        assertThat(result).isEqualTo(CouponRestoreResult.NotReserved)
        verify(metrics).recordAsyncConsumed()
    }
}
