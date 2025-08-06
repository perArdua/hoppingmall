package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.dto.request.PointUseRequest
import com.hoppingmall.mall.point.dto.response.PointUseResponse
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.point.exception.PointInsufficientBalanceException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.emptyFixture
import com.hoppingmall.mall.support.fixture.earnFixture
import com.hoppingmall.mall.support.fixture.useFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

@DisplayName("PointCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointCommandServiceImplTest {

    private val pointRepository: PointRepository = mock()
    private val pointHistoryRepository: PointHistoryRepository = mock()
    private val pointCommandService = PointCommandServiceImpl(pointRepository, pointHistoryRepository)

    @Nested
    @DisplayName("usePoint")
    inner class UsePoint {
        @Test
        fun `포인트 사용 성공`() {
            // given
            val userId = 1L
            val request = PointUseRequest(
                amount = BigDecimal("500"),
                orderId = 1L,
                reason = "상품 구매"
            )
            val point = Point.fixture()
            val savedPoint = Point.fixture(balance = BigDecimal("500"))
            val pointHistory = PointHistory.useFixture()

            whenever(pointRepository.findByUserId(userId)).thenReturn(point)
            whenever(pointRepository.save(any<Point>())).thenReturn(savedPoint)
            whenever(pointHistoryRepository.save(any<PointHistory>())).thenReturn(pointHistory)

            // when
            val response = pointCommandService.usePoint(userId, request)

            // then
            assertEquals(request.amount, response.usedAmount)
            assertEquals(savedPoint.balance, response.remainingBalance)
            assertEquals(request.orderId, response.orderId)
            verify(pointHistoryRepository).save(any())
        }

        @Test
        fun `포인트 잔액 부족 시 예외 발생`() {
            // given
            val userId = 1L
            val request = PointUseRequest(
                amount = BigDecimal("1500"),
                orderId = 1L
            )
            val point = Point.fixture()

            whenever(pointRepository.findByUserId(userId)).thenReturn(point)

            // when & then
            assertThrows(PointInsufficientBalanceException::class.java) {
                pointCommandService.usePoint(userId, request)
            }

            verify(pointRepository, never()).save(any())
            verify(pointHistoryRepository, never()).save(any())
        }

        @Test
        fun `포인트가 없는 사용자는 새로 생성 후 예외 발생`() {
            // given
            val userId = 1L
            val request = PointUseRequest(
                amount = BigDecimal("500"),
                orderId = 1L
            )
            val newPoint = Point.emptyFixture()

            whenever(pointRepository.findByUserId(userId)).thenReturn(null)
            whenever(pointRepository.save(any<Point>())).thenReturn(newPoint)

            // when & then
            assertThrows(PointInsufficientBalanceException::class.java) {
                pointCommandService.usePoint(userId, request)
            }

            verify(pointRepository).save(any())
        }

        @Test
        fun `포인트 사용 내역이 정확히 기록된다`() {
            // given
            val userId = 1L
            val request = PointUseRequest(
                amount = BigDecimal("500"),
                orderId = 1L,
                reason = "상품 구매"
            )
            val point = Point.fixture()
            val savedPoint = Point.fixture(balance = BigDecimal("500"))
            val pointHistory = PointHistory.useFixture()

            whenever(pointRepository.findByUserId(userId)).thenReturn(point)
            whenever(pointRepository.save(any<Point>())).thenReturn(savedPoint)
            whenever(pointHistoryRepository.save(any<PointHistory>())).thenReturn(pointHistory)

            val captor = argumentCaptor<PointHistory>()

            // when
            pointCommandService.usePoint(userId, request)

            // then
            verify(pointHistoryRepository).save(captor.capture())
            val savedHistory = captor.firstValue
            assertEquals(userId, savedHistory.userId)
            assertEquals(request.amount.negate(), savedHistory.amount)
            assertEquals(PointType.USE, savedHistory.type)
            assertEquals(request.reason, savedHistory.reason)
            assertEquals(request.orderId, savedHistory.orderId)
            assertEquals(null, savedHistory.paymentId)
        }
    }

    @Nested
    @DisplayName("validatePointUseRequest")
    inner class ValidatePointUseRequest {
        @Test
        fun `유효한 요청 검증 성공`() {
            // given
            val request = PointUseRequest(
                amount = BigDecimal("500"),
                orderId = 1L
            )
            val point = Point.fixture()
            val savedPoint = Point.fixture(balance = BigDecimal("500"))
            val pointHistory = PointHistory.useFixture()

            whenever(pointRepository.findByUserId(1L)).thenReturn(point)
            whenever(pointRepository.save(any<Point>())).thenReturn(savedPoint)
            whenever(pointHistoryRepository.save(any<PointHistory>())).thenReturn(pointHistory)

            // when & then - 예외가 발생하지 않아야 함
            pointCommandService.usePoint(1L, request)
        }
    }
} 