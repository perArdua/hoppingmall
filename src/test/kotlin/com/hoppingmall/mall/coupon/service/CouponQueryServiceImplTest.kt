package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.domain.Coupon
import com.hoppingmall.mall.coupon.domain.UserCoupon
import com.hoppingmall.mall.coupon.domain.repository.CouponRepository
import com.hoppingmall.mall.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.mall.coupon.exception.CouponNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.util.*

@DisplayName("CouponQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponQueryServiceImplTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    private lateinit var couponQueryService: CouponQueryServiceImpl

    @BeforeEach
    fun setUp() {
        couponQueryService = CouponQueryServiceImpl(couponRepository, userCouponRepository)
    }

    @Nested
    @DisplayName("getAvailableCoupons")
    inner class GetAvailableCoupons {

        @Test
        fun 발급_가능한_쿠폰_목록_조회_성공() {
            // Data
            val coupons = listOf(
                Coupon.fixture(name = "쿠폰1", code = "C001").withId(1L),
                Coupon.fixture(name = "쿠폰2", code = "C002").withId(2L)
            )

            // Context
            whenever(couponRepository.findAvailableCoupons(any(), any())).thenReturn(coupons)

            // Interaction
            val result = couponQueryService.getAvailableCoupons()

            // Assertions
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("쿠폰1")
            assertThat(result[1].name).isEqualTo("쿠폰2")
        }

        @Test
        fun 발급_가능한_쿠폰이_없으면_빈_목록_반환() {
            // Context
            whenever(couponRepository.findAvailableCoupons(any(), any())).thenReturn(emptyList())

            // Interaction
            val result = couponQueryService.getAvailableCoupons()

            // Assertions
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("getAllCoupons")
    inner class GetAllCoupons {

        @Test
        fun 전체_쿠폰_목록_조회_성공() {
            // Data
            val coupons = listOf(
                Coupon.fixture(name = "쿠폰1", code = "C001").withId(1L),
                Coupon.fixture(name = "쿠폰2", code = "C002").withId(2L)
            )

            // Context
            whenever(couponRepository.findAllActive()).thenReturn(coupons)

            // Interaction
            val result = couponQueryService.getAllCoupons()

            // Assertions
            assertThat(result).hasSize(2)
            verify(couponRepository).findAllActive()
        }
    }

    @Nested
    @DisplayName("getMyCoupons")
    inner class GetMyCoupons {

        @Test
        fun 내_쿠폰_목록_조회_성공() {
            // Data
            val userId = 1L
            val pageable = PageRequest.of(0, 20)
            val coupon = Coupon.fixture().withId(1L)
            val userCoupons = listOf(
                UserCoupon.fixture(userId = userId, couponId = 1L).withId(1L)
            )
            val slice = SliceImpl(userCoupons, pageable, false)

            // Context
            whenever(userCouponRepository.findByUserId(userId, pageable)).thenReturn(slice)
            whenever(couponRepository.findAllById(listOf(1L))).thenReturn(listOf(coupon))

            // Interaction
            val result = couponQueryService.getMyCoupons(userId, pageable)

            // Assertions
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].couponName).isEqualTo(coupon.name)
            assertThat(result.hasNext()).isFalse()
            verify(userCouponRepository).findByUserId(userId, pageable)
            verify(couponRepository).findAllById(listOf(1L))
        }

        @Test
        fun 쿠폰이_없으면_빈_슬라이스_반환() {
            // Data
            val pageable = PageRequest.of(0, 20)

            // Context
            whenever(userCouponRepository.findByUserId(1L, pageable))
                .thenReturn(SliceImpl(emptyList(), pageable, false))

            // Interaction
            val result = couponQueryService.getMyCoupons(1L, pageable)

            // Assertions
            assertThat(result.content).isEmpty()
            assertThat(result.hasNext()).isFalse()
        }

        @Test
        fun 쿠폰_원본이_삭제된_경우_예외_발생() {
            // Data
            val pageable = PageRequest.of(0, 20)
            val userCoupons = listOf(UserCoupon.fixture(couponId = 999L).withId(1L))
            val slice = SliceImpl(userCoupons, pageable, false)

            // Context
            whenever(userCouponRepository.findByUserId(1L, pageable)).thenReturn(slice)
            whenever(couponRepository.findAllById(listOf(999L))).thenReturn(emptyList())

            // Interaction & Assertions
            assertThatThrownBy { couponQueryService.getMyCoupons(1L, pageable) }
                .isInstanceOf(CouponNotFoundException::class.java)
        }
    }
}
