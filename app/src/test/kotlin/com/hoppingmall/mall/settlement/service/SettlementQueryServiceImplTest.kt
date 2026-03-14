package com.hoppingmall.mall.settlement.service

import com.hoppingmall.mall.settlement.domain.SettlementItem
import com.hoppingmall.mall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.mall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.mall.settlement.enum.SettlementStatus
import com.hoppingmall.mall.settlement.exception.SettlementAccessDeniedException
import com.hoppingmall.mall.settlement.exception.SettlementNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.settlement.domain.Settlement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.util.Optional

@DisplayName("SettlementQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SettlementQueryServiceImplTest {

    private val settlementRepository: SettlementRepository = mock()
    private val settlementItemRepository: SettlementItemRepository = mock()
    private val service = SettlementQueryServiceImpl(settlementRepository, settlementItemRepository)

    private val pageable = PageRequest.of(0, 20)

    @Nested
    @DisplayName("getSettlements")
    inner class GetSettlements {

        @Test
        fun 판매자별_정산_목록을_조회한다() {
            val settlement = Settlement.fixture(sellerId = 1L).withId(1L)
            whenever(settlementRepository.findBySellerId(any(), any()))
                .thenReturn(PageImpl(listOf(settlement)))

            val result = service.getSettlements(sellerId = 1L, status = null, pageable = pageable)

            assertEquals(1, result.totalElements)
            assertEquals(1L, result.content[0].sellerId)
        }

        @Test
        fun 상태별_정산_목록을_조회한다() {
            val settlement = Settlement.fixture().withId(1L)
            whenever(settlementRepository.findByStatus(any(), any()))
                .thenReturn(PageImpl(listOf(settlement)))

            val result = service.getSettlements(sellerId = null, status = SettlementStatus.CALCULATED, pageable = pageable)

            assertEquals(1, result.totalElements)
        }

        @Test
        fun 판매자_및_상태별_정산_목록을_조회한다() {
            val settlement = Settlement.fixture(sellerId = 1L).withId(1L)
            whenever(settlementRepository.findBySellerIdAndStatus(any(), any(), any()))
                .thenReturn(PageImpl(listOf(settlement)))

            val result = service.getSettlements(sellerId = 1L, status = SettlementStatus.CALCULATED, pageable = pageable)

            assertEquals(1, result.totalElements)
        }

        @Test
        fun 전체_정산_목록을_조회한다() {
            whenever(settlementRepository.findAll(pageable))
                .thenReturn(PageImpl(emptyList()))

            val result = service.getSettlements(sellerId = null, status = null, pageable = pageable)

            assertEquals(0, result.totalElements)
        }
    }

    @Nested
    @DisplayName("getSettlementDetail")
    inner class GetSettlementDetail {

        @Test
        fun 정산_상세를_조회한다() {
            val settlement = Settlement.fixture(sellerId = 1L).withId(1L)
            val item = SettlementItem.fixture(settlementId = 1L).withId(1L)

            whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))
            whenever(settlementItemRepository.findBySettlementId(1L)).thenReturn(listOf(item))

            val result = service.getSettlementDetail(1L, null)

            assertEquals(1L, result.id)
            assertEquals(1, result.items.size)
        }

        @Test
        fun 판매자_본인_정산만_조회_가능하다() {
            val settlement = Settlement.fixture(sellerId = 1L).withId(1L)
            whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

            assertThrows<SettlementAccessDeniedException> {
                service.getSettlementDetail(1L, 999L)
            }
        }

        @Test
        fun 존재하지_않는_정산이면_예외가_발생한다() {
            whenever(settlementRepository.findById(1L)).thenReturn(Optional.empty())

            assertThrows<SettlementNotFoundException> {
                service.getSettlementDetail(1L, null)
            }
        }
    }
}
