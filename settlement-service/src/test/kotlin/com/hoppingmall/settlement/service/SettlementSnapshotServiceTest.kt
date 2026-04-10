package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementRepository
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
@DisplayName("SettlementSnapshotService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementSnapshotServiceTest {

    @Mock
    private lateinit var settlementRepository: SettlementRepository

    @Mock
    private lateinit var settlementSummaryRepository: SettlementSummaryRepository

    @InjectMocks
    private lateinit var settlementSnapshotService: SettlementSnapshotService

    private fun createSettlement(id: Long): Settlement {
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

    @Test
    fun 스냅샷_생성_시_신규_Summary를_생성한다() {
        val settlement = createSettlement(1L)

        whenever(settlementRepository.findAll()).thenReturn(listOf(settlement))
        whenever(settlementSummaryRepository.findBySettlementIdIn(listOf(1L))).thenReturn(emptyList())
        whenever(settlementSummaryRepository.saveAll(any<List<SettlementSummary>>())).thenAnswer { it.arguments[0] }

        settlementSnapshotService.createSnapshot()

        verify(settlementSummaryRepository).saveAll(any<List<SettlementSummary>>())
    }

    @Test
    fun 기존_Summary가_있으면_업데이트한다() {
        val settlement = createSettlement(1L)
        val existingSummary = SettlementSummary.from(settlement)

        whenever(settlementRepository.findAll()).thenReturn(listOf(settlement))
        whenever(settlementSummaryRepository.findBySettlementIdIn(listOf(1L))).thenReturn(listOf(existingSummary))

        settlementSnapshotService.createSnapshot()

        verify(settlementSummaryRepository, never()).saveAll(any<List<SettlementSummary>>())
    }
}
