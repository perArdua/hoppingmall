package com.hoppingmall.payment.point.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.dto.request.PointUseRequest
import com.hoppingmall.payment.point.exception.PointInsufficientBalanceException
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
@DisplayName("PointCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointCommandServiceImplTest {

    @Mock
    private lateinit var pointRepository: PointRepository

    @Mock
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Mock
    private lateinit var pointDomainService: PointDomainService

    @InjectMocks
    private lateinit var pointCommandService: PointCommandServiceImpl

    @Test
    fun 포인트_사용_성공() {
        val point = Point(userId = 1L, balance = BigDecimal("5000"))
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(point, 1L)
        whenever(pointDomainService.findOrCreatePoint(1L)).thenReturn(point)
        doAnswer { it.arguments[0] }.whenever(pointRepository).save(any())
        doAnswer { it.arguments[0] }.whenever(pointHistoryRepository).save(any())

        val request = PointUseRequest(amount = BigDecimal("1000"), orderId = 10L, reason = "구매")
        val result = pointCommandService.usePoint(1L, request)

        assertThat(result.usedAmount).isEqualByComparingTo(BigDecimal("1000"))
        assertThat(result.remainingBalance).isEqualByComparingTo(BigDecimal("4000"))
        verify(pointHistoryRepository).save(any())
    }

    @Test
    fun 잔액_부족_시_예외가_발생한다() {
        val point = Point(userId = 1L, balance = BigDecimal("100"))
        whenever(pointDomainService.findOrCreatePoint(1L)).thenReturn(point)

        val request = PointUseRequest(amount = BigDecimal("500"), orderId = 10L, reason = "구매")

        assertThatThrownBy { pointCommandService.usePoint(1L, request) }
            .isInstanceOf(PointInsufficientBalanceException::class.java)
    }

    @Test
    fun 포인트_환불_성공() {
        val point = Point(userId = 1L, balance = BigDecimal("1000"))
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(point, 1L)
        whenever(pointHistoryRepository.existsByEventId("refund-point-10-20")).thenReturn(false)
        whenever(pointDomainService.findOrCreatePoint(1L)).thenReturn(point)
        doAnswer { it.arguments[0] }.whenever(pointRepository).save(any())
        doAnswer { it.arguments[0] }.whenever(pointHistoryRepository).save(any())

        pointCommandService.refundPoints(1L, BigDecimal("500"), 10L, 20L)

        assertThat(point.balance).isEqualByComparingTo(BigDecimal("1500"))
        verify(pointHistoryRepository).save(any())
    }

    @Test
    fun 이미_환불된_이벤트면_중복_환불하지_않는다() {
        whenever(pointHistoryRepository.existsByEventId("refund-point-10-20")).thenReturn(true)

        pointCommandService.refundPoints(1L, BigDecimal("500"), 10L, 20L)

        verify(pointDomainService, never()).findOrCreatePoint(any())
    }

    @Test
    fun 환불_금액이_0_이하면_처리하지_않는다() {
        pointCommandService.refundPoints(1L, BigDecimal.ZERO, 10L, 20L)

        verify(pointHistoryRepository, never()).existsByEventId(any())
    }
}
