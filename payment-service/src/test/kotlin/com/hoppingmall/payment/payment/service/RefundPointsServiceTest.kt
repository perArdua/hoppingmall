package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("RefundPointsService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundPointsServiceTest {

    @Mock
    private lateinit var pointRepository: PointRepository

    @Mock
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @InjectMocks
    private lateinit var service: RefundPointsService

    @Test
    fun 적립_내역이_존재하면_포인트를_차감하고_환불_이력을_저장한다() {
        val earnHistory = PointHistory(
            userId = 1L,
            amount = BigDecimal("500"),
            type = PointType.EARN,
            paymentId = 10L,
            eventId = "earn-10"
        )
        val point = Point(userId = 1L, balance = BigDecimal("1000"))

        whenever(pointHistoryRepository.findByPaymentIdAndType(10L, PointType.EARN)).thenReturn(earnHistory)
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(point)
        whenever(pointRepository.save(point)).thenReturn(point)
        whenever(pointHistoryRepository.save(any<PointHistory>())).thenAnswer { it.arguments[0] }

        service.refundPoints(1L, 10L)

        verify(pointRepository).save(point)
        verify(pointHistoryRepository).save(any<PointHistory>())
    }

    @Test
    fun 적립_내역이_없으면_포인트_처리를_하지_않는다() {
        whenever(pointHistoryRepository.findByPaymentIdAndType(10L, PointType.EARN)).thenReturn(null)

        service.refundPoints(1L, 10L)

        verify(pointRepository, never()).findByUserIdForUpdate(any())
        verify(pointRepository, never()).save(any())
        verify(pointHistoryRepository, never()).save(any())
    }

    @Test
    fun 포인트_엔티티가_없으면_이력_저장을_하지_않는다() {
        val earnHistory = PointHistory(
            userId = 1L,
            amount = BigDecimal("500"),
            type = PointType.EARN,
            paymentId = 10L,
            eventId = "earn-10"
        )

        whenever(pointHistoryRepository.findByPaymentIdAndType(10L, PointType.EARN)).thenReturn(earnHistory)
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(null)

        service.refundPoints(1L, 10L)

        verify(pointRepository, never()).save(any())
        verify(pointHistoryRepository, never()).save(any())
    }
}
