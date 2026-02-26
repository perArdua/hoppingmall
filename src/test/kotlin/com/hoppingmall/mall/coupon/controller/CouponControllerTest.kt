package com.hoppingmall.mall.coupon.controller

import com.hoppingmall.mall.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse
import com.hoppingmall.mall.coupon.enum.CouponStatus
import com.hoppingmall.mall.coupon.enum.DiscountType
import com.hoppingmall.mall.coupon.enum.UserCouponStatus
import com.hoppingmall.mall.coupon.service.CouponCommandService
import com.hoppingmall.mall.coupon.service.CouponQueryService
import com.hoppingmall.mall.global.auth.UserPrincipal
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("CouponController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CouponControllerTest {

    private val couponCommandService: CouponCommandService = mock()
    private val couponQueryService: CouponQueryService = mock()
    private val controller = CouponController(couponCommandService, couponQueryService)

    private fun createCouponResponse(
        id: Long = 1L,
        name: String = "테스트 쿠폰",
        code: String = "TEST001"
    ): CouponResponse {
        return CouponResponse(
            id = id,
            name = name,
            code = code,
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("5000"),
            minOrderAmount = BigDecimal("10000"),
            maxDiscountAmount = null,
            totalQuantity = 100,
            issuedQuantity = 0,
            validFrom = LocalDateTime.of(2026, 1, 1, 0, 0),
            validTo = LocalDateTime.of(2026, 12, 31, 23, 59),
            status = CouponStatus.ACTIVE,
            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        )
    }

    private fun createUserCouponResponse(
        id: Long = 1L,
        couponId: Long = 1L
    ): UserCouponResponse {
        return UserCouponResponse(
            id = id,
            couponId = couponId,
            couponName = "테스트 쿠폰",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("5000"),
            minOrderAmount = BigDecimal("10000"),
            maxDiscountAmount = null,
            status = UserCouponStatus.ISSUED,
            usedAt = null,
            validFrom = LocalDateTime.of(2026, 1, 1, 0, 0),
            validTo = LocalDateTime.of(2026, 12, 31, 23, 59),
            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        )
    }

    @Nested
    @DisplayName("createCoupon")
    inner class CreateCoupon {
        @Test
        fun 쿠폰_생성_성공() {
            // Data
            val request = CouponCreateRequest(
                name = "테스트 쿠폰",
                code = "TEST001",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal("5000"),
                minOrderAmount = BigDecimal("10000"),
                maxDiscountAmount = null,
                totalQuantity = 100,
                validFrom = LocalDateTime.of(2026, 1, 1, 0, 0),
                validTo = LocalDateTime.of(2026, 12, 31, 23, 59)
            )
            val expectedResponse = createCouponResponse()

            // Context
            whenever(couponCommandService.createCoupon(request)).thenReturn(expectedResponse)

            // Interaction
            val response = controller.createCoupon(request)

            // Assertions
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(couponCommandService).createCoupon(request)
        }
    }

    @Nested
    @DisplayName("getAllCoupons")
    inner class GetAllCoupons {
        @Test
        fun 전체_쿠폰_목록_조회_성공() {
            // Data
            val expectedResponse = listOf(createCouponResponse())

            // Context
            whenever(couponQueryService.getAllCoupons()).thenReturn(expectedResponse)

            // Interaction
            val response = controller.getAllCoupons()

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(couponQueryService).getAllCoupons()
        }
    }

    @Nested
    @DisplayName("changeCouponStatus")
    inner class ChangeCouponStatus {
        @Test
        fun 쿠폰_상태_변경_성공() {
            // Data
            val expectedResponse = createCouponResponse()

            // Context
            whenever(couponCommandService.changeCouponStatus(1L, CouponStatus.INACTIVE))
                .thenReturn(expectedResponse)

            // Interaction
            val response = controller.changeCouponStatus(1L, CouponStatus.INACTIVE)

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            verify(couponCommandService).changeCouponStatus(1L, CouponStatus.INACTIVE)
        }
    }

    @Nested
    @DisplayName("getAvailableCoupons")
    inner class GetAvailableCoupons {
        @Test
        fun 발급_가능_쿠폰_목록_조회_성공() {
            // Data
            val expectedResponse = listOf(createCouponResponse())

            // Context
            whenever(couponQueryService.getAvailableCoupons()).thenReturn(expectedResponse)

            // Interaction
            val response = controller.getAvailableCoupons()

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(couponQueryService).getAvailableCoupons()
        }
    }

    @Nested
    @DisplayName("issueCoupon")
    inner class IssueCoupon {
        @Test
        fun 쿠폰_발급_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
            val expectedResponse = createUserCouponResponse()

            // Context
            whenever(couponCommandService.issueCoupon(userPrincipal.getUserId(), 1L))
                .thenReturn(expectedResponse)

            // Interaction
            val response = controller.issueCoupon(userPrincipal, 1L)

            // Assertions
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(couponCommandService).issueCoupon(userPrincipal.getUserId(), 1L)
        }
    }

    @Nested
    @DisplayName("getMyCoupons")
    inner class GetMyCoupons {
        @Test
        fun 내_쿠폰_목록_조회_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
            val expectedResponse = listOf(createUserCouponResponse())

            // Context
            whenever(couponQueryService.getMyCoupons(userPrincipal.getUserId()))
                .thenReturn(expectedResponse)

            // Interaction
            val response = controller.getMyCoupons(userPrincipal)

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(couponQueryService).getMyCoupons(userPrincipal.getUserId())
        }
    }
}
