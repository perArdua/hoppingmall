package com.hoppingmall.payment.coupon.infrastructure

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("CouponStockReconciliationScheduler")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponStockReconciliationSchedulerTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @Mock
    private lateinit var couponStockRedisRepository: CouponStockRedisRepository

    @InjectMocks
    private lateinit var scheduler: CouponStockReconciliationScheduler

    private fun createCoupon(id: Long): Coupon {
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
    fun ACTIVE_쿠폰이_없으면_아무것도_하지_않는다() {
        whenever(couponRepository.findAllActive()).thenReturn(emptyList())

        scheduler.reconcile()

        verify(couponStockRedisRepository, never()).getIssuedUserIds(org.mockito.kotlin.any())
    }

    @Test
    fun Redis_set이_비어있으면_DB_조회를_건너뛴다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(createCoupon(1L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenReturn(emptySet())

        scheduler.reconcile()

        verify(userCouponRepository, never()).findUserIdsByCouponId(org.mockito.kotlin.any())
        verify(couponStockRedisRepository, never()).restoreStock(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun 고스트가_없으면_복원하지_않는다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(createCoupon(1L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenReturn(setOf(10L, 20L))
        whenever(userCouponRepository.findUserIdsByCouponId(1L)).thenReturn(listOf(10L, 20L))

        scheduler.reconcile()

        verify(couponStockRedisRepository, never()).restoreStock(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun 고스트_사용자만_restoreStock으로_보정한다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(createCoupon(1L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenReturn(setOf(10L, 20L, 30L))
        whenever(userCouponRepository.findUserIdsByCouponId(1L)).thenReturn(listOf(10L))

        scheduler.reconcile()

        verify(couponStockRedisRepository).restoreStock(eq(1L), eq(20L))
        verify(couponStockRedisRepository).restoreStock(eq(1L), eq(30L))
        verify(couponStockRedisRepository, never()).restoreStock(eq(1L), eq(10L))
    }

    @Test
    fun Redis_조회_실패_시_해당_쿠폰을_건너뛴다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(createCoupon(1L), createCoupon(2L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenThrow(RuntimeException("Redis down"))
        whenever(couponStockRedisRepository.getIssuedUserIds(2L)).thenReturn(setOf(10L))
        whenever(userCouponRepository.findUserIdsByCouponId(2L)).thenReturn(emptyList())

        scheduler.reconcile()

        verify(couponStockRedisRepository).restoreStock(eq(2L), eq(10L))
    }

    @Test
    fun 복원_실패해도_다음_고스트를_계속_처리한다() {
        whenever(couponRepository.findAllActive()).thenReturn(listOf(createCoupon(1L)))
        whenever(couponStockRedisRepository.getIssuedUserIds(1L)).thenReturn(setOf(10L, 20L))
        whenever(userCouponRepository.findUserIdsByCouponId(1L)).thenReturn(emptyList())
        whenever(couponStockRedisRepository.restoreStock(eq(1L), eq(10L))).thenThrow(RuntimeException("partial fail"))

        scheduler.reconcile()

        verify(couponStockRedisRepository).restoreStock(eq(1L), eq(20L))
    }
}
