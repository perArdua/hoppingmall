package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.dto.response.PointBalanceResponse
import com.hoppingmall.mall.point.dto.response.PointHistoryResponse
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.emptyFixture
import com.hoppingmall.mall.support.fixture.earnFixture
import com.hoppingmall.mall.support.fixture.useFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PointQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointQueryServiceImplTest {

    private val pointRepository: PointRepository = mock()
    private val pointHistoryRepository: PointHistoryRepository = mock()
    private val pointQueryService = PointQueryServiceImpl(pointRepository, pointHistoryRepository)

    @Nested
    @DisplayName("getPointBalance")
    inner class GetPointBalance {
        @Test
        fun 포인트_잔액_조회_성공() {
            val userId = 1L
            val point = Point.fixture()

            whenever(pointRepository.findByUserId(userId)).thenReturn(point)

            val response = pointQueryService.getPointBalance(userId)

            assertEquals(PointBalanceResponse(BigDecimal("1000")), response)
            verify(pointRepository).findByUserId(userId)
        }

        @Test
        fun 포인트가_없는_사용자의_잔액은_0() {
            val userId = 1L

            whenever(pointRepository.findByUserId(userId)).thenReturn(null)

            val response = pointQueryService.getPointBalance(userId)

            assertEquals(PointBalanceResponse(BigDecimal.ZERO), response)
            verify(pointRepository).findByUserId(userId)
        }
    }

    @Nested
    @DisplayName("getPointHistory")
    inner class GetPointHistory {
        @Test
        fun 포인트_내역_조회_성공() {
            val userId = 1L
            val pageable = PageRequest.of(0, 10)

            val pointHistory1 = PointHistory.earnFixture()
            val pointHistory2 = PointHistory.useFixture()

            val histories = listOf(pointHistory1, pointHistory2)
            val page = PageImpl(histories, pageable, 2)

            whenever(pointHistoryRepository.findByUserId(userId, pageable)).thenReturn(page)

            val response = pointQueryService.getPointHistory(userId, pageable)

            assertEquals(2, response.content.size)
            assertEquals(PointType.EARN, response.content[0].type)
            assertEquals(PointType.USE, response.content[1].type)
            verify(pointHistoryRepository).findByUserId(userId, pageable)
        }

        @Test
        fun 빈_포인트_내역_조회() {
            val userId = 1L
            val pageable = PageRequest.of(0, 10)
            val emptyPage = PageImpl<PointHistory>(emptyList(), pageable, 0)

            whenever(pointHistoryRepository.findByUserId(userId, pageable)).thenReturn(emptyPage)

            val response = pointQueryService.getPointHistory(userId, pageable)

            assertEquals(0, response.content.size)
            verify(pointHistoryRepository).findByUserId(userId, pageable)
        }
    }
} 