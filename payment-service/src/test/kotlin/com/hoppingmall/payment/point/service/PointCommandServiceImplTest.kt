package com.hoppingmall.payment.point.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.dto.request.PointUseRequest
import com.hoppingmall.payment.point.enum.PointType
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
import org.mockito.kotlin.argumentCaptor
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
        whenever(pointHistoryRepository.existsByEventId("refund-point-30")).thenReturn(false)
        whenever(pointDomainService.findOrCreatePoint(1L)).thenReturn(point)
        whenever(pointHistoryRepository.findAllByUserIdAndPaymentIdForUpdate(1L, 10L))
            .thenReturn(listOf(history(BigDecimal("-500"), PointType.USE, "use-10")))
        doAnswer { it.arguments[0] }.whenever(pointRepository).save(any())
        doAnswer { it.arguments[0] }.whenever(pointHistoryRepository).save(any())

        pointCommandService.refundPoints(1L, BigDecimal("500"), 10L, 20L, 30L)

        assertThat(point.balance).isEqualByComparingTo(BigDecimal("1500"))
        verify(pointHistoryRepository).save(any())
    }

    @Test
    fun 환불로_되돌리는_포인트는_결제에_쓰고_남은_만큼을_넘지_않는다() {
        val point = Point(userId = 1L, balance = BigDecimal("1000"))
        whenever(pointHistoryRepository.existsByEventId("refund-point-31")).thenReturn(false)
        whenever(pointDomainService.findOrCreatePoint(1L)).thenReturn(point)
        whenever(pointHistoryRepository.findAllByUserIdAndPaymentIdForUpdate(1L, 10L)).thenReturn(
            listOf(
                history(BigDecimal("-500"), PointType.USE, "use-10"),
                history(BigDecimal("200"), PointType.REFUND, "refund-point-30"),
                history(BigDecimal("-50"), PointType.RECLAIM, "refund-10-30"),
                history(BigDecimal("40"), PointType.REFUND, "refund-10")
            )
        )
        doAnswer { it.arguments[0] }.whenever(pointRepository).save(any())
        val saved = argumentCaptor<PointHistory>()
        doAnswer { it.arguments[0] }.whenever(pointHistoryRepository).save(saved.capture())

        pointCommandService.refundPoints(1L, BigDecimal("500"), 10L, 20L, 31L)

        assertThat(point.balance).isEqualByComparingTo(BigDecimal("1300"))
        assertThat(saved.firstValue.amount).isEqualByComparingTo(BigDecimal("300"))
        assertThat(saved.firstValue.eventId).isEqualTo("refund-point-31")
    }

    @Test
    fun 결제_취소의_되돌림은_남은_사용_포인트_전부이고_남은_것이_없으면_이력을_남기지_않는다() {
        val point = Point(userId = 1L, balance = BigDecimal("1000"))
        whenever(pointHistoryRepository.existsByEventId("use-cancel-10")).thenReturn(false)
        whenever(pointDomainService.findOrCreatePoint(1L)).thenReturn(point)
        whenever(pointHistoryRepository.findAllByUserIdAndPaymentIdForUpdate(1L, 10L)).thenReturn(
            listOf(
                history(BigDecimal("-500"), PointType.USE, "use-10"),
                history(BigDecimal("500"), PointType.REFUND, "refund-point-30")
            )
        )

        val restored = pointCommandService.restoreUsedPoints(1L, 10L, 20L, "use-cancel-10", "결제 취소 포인트 반환")

        assertThat(restored).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(point.balance).isEqualByComparingTo(BigDecimal("1000"))
        verify(pointHistoryRepository, never()).save(any())
    }

    @Test
    fun 결제에_포인트를_쓰면_잔액을_줄이고_사용_이력을_결제에_묶어_남긴다() {
        val point = Point(userId = 1L, balance = BigDecimal("5000"))
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(point)
        doAnswer { it.arguments[0] }.whenever(pointRepository).save(any())
        val saved = argumentCaptor<PointHistory>()
        doAnswer { it.arguments[0] }.whenever(pointHistoryRepository).save(saved.capture())

        pointCommandService.useForPayment(1L, 10L, 20L, BigDecimal("1200"))

        assertThat(point.balance).isEqualByComparingTo(BigDecimal("3800"))
        assertThat(saved.firstValue.type).isEqualTo(PointType.USE)
        assertThat(saved.firstValue.amount).isEqualByComparingTo(BigDecimal("-1200"))
        assertThat(saved.firstValue.paymentId).isEqualTo(10L)
        assertThat(saved.firstValue.eventId).isEqualTo("use-10")
    }

    @Test
    fun 포인트_행이_없는_사용자가_결제에_포인트를_쓰면_잔액_부족이다() {
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(null)

        assertThatThrownBy { pointCommandService.useForPayment(1L, 10L, 20L, BigDecimal("100")) }
            .isInstanceOf(PointInsufficientBalanceException::class.java)
    }

    @Test
    fun 이미_환불된_이벤트면_중복_환불하지_않는다() {
        whenever(pointHistoryRepository.existsByEventId("refund-point-30")).thenReturn(true)

        pointCommandService.refundPoints(1L, BigDecimal("500"), 10L, 20L, 30L)

        verify(pointDomainService, never()).findOrCreatePoint(any())
    }

    @Test
    fun 환불_금액이_0_이하면_처리하지_않는다() {
        pointCommandService.refundPoints(1L, BigDecimal.ZERO, 10L, 20L, 30L)

        verify(pointHistoryRepository, never()).existsByEventId(any())
    }

    private fun history(amount: BigDecimal, type: PointType, eventId: String) = PointHistory(
        userId = 1L,
        amount = amount,
        type = type,
        paymentId = 10L,
        eventId = eventId
    )
}
