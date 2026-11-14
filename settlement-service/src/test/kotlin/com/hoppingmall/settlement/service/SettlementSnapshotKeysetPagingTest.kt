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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Limit
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
@DisplayName("정산 스냅샷 잡의 정산 ID 기준 페이지")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementSnapshotKeysetPagingTest {

    @Mock
    private lateinit var settlementRepository: SettlementRepository

    @Mock
    private lateinit var settlementSummaryRepository: SettlementSummaryRepository

    @InjectMocks
    private lateinit var settlementSnapshotService: SettlementSnapshotService

    @Test
    fun 다음_묶음은_OFFSET이_아니라_앞_묶음의_마지막_정산_ID_다음부터_읽는다() {
        val first = (1L..BATCH_SIZE.toLong()).map { settlement(it) }
        val second = listOf(settlement(BATCH_SIZE + 7L))
        whenever(settlementRepository.findByIdGreaterThanOrderByIdAsc(0L, Limit.of(BATCH_SIZE))).thenReturn(first)
        whenever(settlementRepository.findByIdGreaterThanOrderByIdAsc(BATCH_SIZE.toLong(), Limit.of(BATCH_SIZE))).thenReturn(second)
        whenever(settlementSummaryRepository.findBySettlementIdIn(any())).thenReturn(emptyList())
        whenever(settlementSummaryRepository.saveAll(any<List<SettlementSummary>>())).thenAnswer { it.arguments[0] }

        settlementSnapshotService.createSnapshot()

        val order = inOrder(settlementRepository)
        order.verify(settlementRepository).findByIdGreaterThanOrderByIdAsc(0L, Limit.of(BATCH_SIZE))
        order.verify(settlementRepository).findByIdGreaterThanOrderByIdAsc(BATCH_SIZE.toLong(), Limit.of(BATCH_SIZE))
        verify(settlementRepository, never()).findAll(anyOrNull<Pageable>())
        verify(settlementSummaryRepository).findBySettlementIdIn(listOf(BATCH_SIZE + 7L))
    }

    @Test
    fun 묶음이_가득_차지_않으면_다음_묶음을_더_읽지_않는다() {
        whenever(settlementRepository.findByIdGreaterThanOrderByIdAsc(0L, Limit.of(BATCH_SIZE))).thenReturn(listOf(settlement(3L)))
        whenever(settlementSummaryRepository.findBySettlementIdIn(listOf(3L))).thenReturn(emptyList())
        whenever(settlementSummaryRepository.saveAll(any<List<SettlementSummary>>())).thenAnswer { it.arguments[0] }

        settlementSnapshotService.createSnapshot()

        verify(settlementRepository, never()).findByIdGreaterThanOrderByIdAsc(3L, Limit.of(BATCH_SIZE))
        verify(settlementRepository, never()).findAll(anyOrNull<Pageable>())
    }

    private fun settlement(id: Long): Settlement {
        val settlement = Settlement.create(
            sellerId = id,
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

    companion object {
        private const val BATCH_SIZE = 200
    }
}
