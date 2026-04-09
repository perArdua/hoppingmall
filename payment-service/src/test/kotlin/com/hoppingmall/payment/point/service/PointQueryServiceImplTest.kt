package com.hoppingmall.payment.point.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("PointQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointQueryServiceImplTest {

    @Mock
    private lateinit var pointRepository: PointRepository

    @Mock
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @InjectMocks
    private lateinit var pointQueryService: PointQueryServiceImpl

    @Test
    fun 포인트_잔액을_조회한다() {
        val point = Point(userId = 1L, balance = BigDecimal("5000"))
        whenever(pointRepository.findByUserId(1L)).thenReturn(point)

        val result = pointQueryService.getPointBalance(1L)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("5000"))
    }

    @Test
    fun 포인트가_없으면_잔액_0을_반환한다() {
        whenever(pointRepository.findByUserId(1L)).thenReturn(null)

        val result = pointQueryService.getPointBalance(1L)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 포인트_이력을_페이지네이션으로_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val history = PointHistory(
            userId = 1L,
            amount = BigDecimal("100"),
            type = PointType.EARN,
            reason = "적립"
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(history, 1L)
        val createdAtField = BaseEntity::class.java.getDeclaredField("createdAt")
        createdAtField.isAccessible = true
        createdAtField.set(history, LocalDateTime.now())

        val slice = SliceImpl(listOf(history), pageable, false)
        whenever(pointHistoryRepository.findByUserId(1L, pageable)).thenReturn(slice)

        val result = pointQueryService.getPointHistory(1L, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].amount).isEqualByComparingTo(BigDecimal("100"))
    }
}
