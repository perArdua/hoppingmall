package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
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
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
@DisplayName("정산 요약 동기화의 요약 표 확인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementSummarySynchronizerTest {

    @Mock
    private lateinit var settlementSummaryRepository: SettlementSummaryRepository

    @InjectMocks
    private lateinit var settlementSummarySynchronizer: SettlementSummarySynchronizer

    @Test
    fun 요약_표에_행이_있으면_전체를_세지_않고_한_행이_있는지만_보고_요약을_만든다() {
        val settlement = settlement(11L)
        whenever(settlementSummaryRepository.findBySettlementId(11L)).thenReturn(null)
        whenever(settlementSummaryRepository.existsByIdIsNotNull()).thenReturn(true)

        settlementSummarySynchronizer.sync(settlement)

        verify(settlementSummaryRepository).save(any<SettlementSummary>())
        verify(settlementSummaryRepository, never()).count()
    }

    @Test
    fun 요약_표가_비었으면_전체를_세지_않고_요약을_만들지_않는다() {
        val settlement = settlement(12L)
        whenever(settlementSummaryRepository.findBySettlementId(12L)).thenReturn(null)
        whenever(settlementSummaryRepository.existsByIdIsNotNull()).thenReturn(false)

        settlementSummarySynchronizer.sync(settlement)

        verify(settlementSummaryRepository, never()).save(any<SettlementSummary>())
        verify(settlementSummaryRepository, never()).count()
    }

    private fun settlement(id: Long): Settlement {
        val settlement = Settlement.create(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("100000"),
            totalRefundAmount = BigDecimal("5000"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("5000.00"),
            settlementAmount = BigDecimal("90000.00")
        )
        ReflectionTestUtils.setField(settlement, "id", id)
        return settlement
    }
}
