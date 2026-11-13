package com.hoppingmall.payment.coupon.infrastructure

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.service.CouponCommandServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("쿠폰 정합성 스윕의 예약 나이 기준")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponStockReconciliationReservationAgeTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @Mock
    private lateinit var couponStockRedisRepository: CouponStockRedisRepository

    @InjectMocks
    private lateinit var scheduler: CouponStockReconciliationScheduler

    private fun activeCoupon(id: Long): Coupon {
        val coupon = Coupon.create(
            name = "쿠폰",
            code = "C$id",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("1000"),
            minOrderAmount = BigDecimal("10000"),
            maxDiscountAmount = BigDecimal("1000"),
            totalQuantity = 100,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(7)
        )
        coupon.changeStatus(CouponStatus.ACTIVE)
        ReflectionTestUtils.setField(coupon, "id", id)
        return coupon
    }

    @Test
    fun DB_조회_전_Redis_시각에서_최소_나이를_뺀_시각보다_뒤에_예약한_사용자는_복원하지_않는다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(activeCoupon(1L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenReturn(setOf(10L, 20L, 30L))
        whenever(couponStockRedisRepository.reservationClockMillis(1L)).thenReturn(1_000_000L)
        whenever(userCouponRepository.findUserIdsByCouponId(1L)).thenReturn(listOf(30L))
        whenever(couponStockRedisRepository.findReservedAfter(eq(1L), eq(setOf(10L, 20L)), eq(700_000L)))
            .thenReturn(setOf(20L))

        scheduler.reconcile()

        verify(couponStockRedisRepository).restoreStock(1L, 10L)
        verify(couponStockRedisRepository, never()).restoreStock(1L, 20L)
        verify(couponStockRedisRepository, never()).restoreStock(1L, 30L)
    }

    @Test
    fun Redis_시각_조회가_실패하면_그_쿠폰은_DB를_읽지_않고_건너뛴다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(activeCoupon(1L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenReturn(setOf(10L))
        whenever(couponStockRedisRepository.reservationClockMillis(1L)).thenThrow(RuntimeException("Redis down"))

        scheduler.reconcile()

        verify(userCouponRepository, never()).findUserIdsByCouponId(any())
        verify(couponStockRedisRepository, never()).restoreStock(any(), any())
    }

    @Test
    fun 예약_시각_조회가_실패하면_그_쿠폰은_복원하지_않는다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(activeCoupon(1L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenReturn(setOf(10L))
        whenever(userCouponRepository.findUserIdsByCouponId(1L)).thenReturn(emptyList())
        whenever(couponStockRedisRepository.findReservedAfter(any(), any(), any()))
            .thenThrow(RuntimeException("Redis down"))

        scheduler.reconcile()

        verify(couponStockRedisRepository, never()).restoreStock(any(), any())
    }

    @Test
    fun 최소_나이가_발급_트랜잭션_제한_시간보다_길지_않으면_기동을_막는다() {
        ReflectionTestUtils.setField(scheduler, "minReservationAgeMs", 30_000L)

        assertThatThrownBy { scheduler.validateMinReservationAge() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 기본_최소_나이는_발급_트랜잭션_제한_시간보다_길다() {
        assertThatCode { scheduler.validateMinReservationAge() }.doesNotThrowAnyException()
    }

    @Test
    fun 쿠폰_발급_트랜잭션에는_제한_시간이_걸려_있다() {
        val issueCoupon = CouponCommandServiceImpl::class.java
            .getMethod("issueCoupon", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)

        assertThat(issueCoupon.getAnnotation(Transactional::class.java).timeout)
            .isEqualTo(CouponCommandServiceImpl.ISSUE_TRANSACTION_TIMEOUT_SECONDS)
    }
}
