package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementRefund
import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRefundRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import com.hoppingmall.settlement.enums.SettlementStatus
import com.hoppingmall.settlement.exception.SettlementAccessDeniedException
import com.hoppingmall.settlement.exception.SettlementNotFoundException
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
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@DisplayName("SettlementQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementQueryServiceImplTest {

    @Mock
    private lateinit var settlementRepository: SettlementRepository

    @Mock
    private lateinit var settlementItemRepository: SettlementItemRepository

    @Mock
    private lateinit var settlementSummaryRepository: SettlementSummaryRepository

    @Mock
    private lateinit var settlementRefundRepository: SettlementRefundRepository

    @InjectMocks
    private lateinit var settlementQueryServiceImpl: SettlementQueryServiceImpl

    private fun createSummary(): SettlementSummary {
        val summary = SettlementSummary(
            settlementId = 1L,
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("100000"),
            totalRefundAmount = BigDecimal("5000"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("5000"),
            settlementAmount = BigDecimal("90000"),
            status = SettlementStatus.CALCULATED
        )
        ReflectionTestUtils.setField(summary, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0))
        return summary
    }

    private fun createSettlement(): Settlement {
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
        ReflectionTestUtils.setField(settlement, "id", 1L)
        ReflectionTestUtils.setField(settlement, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0))
        return settlement
    }

    @Test
    fun Summary_있을_때_Summary에서_목록을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val summary = createSummary()
        val summaryPage = PageImpl(listOf(summary), pageable, 1)

        whenever(settlementSummaryRepository.findBySellerId(any(), any())).thenReturn(summaryPage)

        val result = settlementQueryServiceImpl.getSettlements(1L, null, pageable)

        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.content[0].sellerId).isEqualTo(1L)
        assertThat(result.content[0].totalSalesAmount).isEqualByComparingTo(BigDecimal("100000"))
    }

    @Test
    fun Summary_비어있을_때_write_model에서_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val settlement = createSettlement()
        val emptyPage = PageImpl<SettlementSummary>(emptyList(), pageable, 0)
        val settlementPage = PageImpl(listOf(settlement), pageable, 1)

        whenever(settlementSummaryRepository.findBySellerId(any(), any())).thenReturn(emptyPage)
        whenever(settlementRepository.findBySellerId(any(), any())).thenReturn(settlementPage)

        val result = settlementQueryServiceImpl.getSettlements(1L, null, pageable)

        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.content[0].sellerId).isEqualTo(1L)
    }

    @Test
    fun 정산_상세_조회_시_write_model을_사용한다() {
        val settlement = createSettlement()

        whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))
        whenever(settlementItemRepository.findBySettlementId(1L)).thenReturn(emptyList())

        val result = settlementQueryServiceImpl.getSettlementDetail(1L, 1L)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.sellerId).isEqualTo(1L)
    }

    @Test
    fun 정산_상세는_이월_금액과_차감한_환불을_함께_보여준다() {
        val settlement = Settlement.create(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 2, 1),
            periodEnd = LocalDate.of(2026, 2, 28),
            totalSalesAmount = BigDecimal.ZERO,
            totalRefundAmount = BigDecimal("30000"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("-1500.00"),
            settlementAmount = BigDecimal.ZERO,
            carriedInAmount = BigDecimal("1000.00"),
            carryOverAmount = BigDecimal("29500.00")
        )
        ReflectionTestUtils.setField(settlement, "id", 2L)
        val refund = SettlementRefund.create(settlementId = 2L, refundId = 901L, refundAmount = BigDecimal("30000"))

        whenever(settlementRepository.findById(2L)).thenReturn(Optional.of(settlement))
        whenever(settlementItemRepository.findBySettlementId(2L)).thenReturn(emptyList())
        whenever(settlementRefundRepository.findBySettlementId(2L)).thenReturn(listOf(refund))

        val result = settlementQueryServiceImpl.getSettlementDetail(2L, 1L)

        assertThat(result.carriedInAmount).isEqualByComparingTo("1000")
        assertThat(result.carryOverAmount).isEqualByComparingTo("29500")
        assertThat(result.refunds.map { it.refundId to it.refundAmount }).containsExactly(901L to BigDecimal("30000"))
        assertThat(refund.settlementId).isEqualTo(2L)
    }

    @Test
    fun 다른_판매자의_정산_상세_조회_시_예외가_발생한다() {
        val settlement = createSettlement()

        whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

        assertThatThrownBy { settlementQueryServiceImpl.getSettlementDetail(1L, 99L) }
            .isInstanceOf(SettlementAccessDeniedException::class.java)
    }

    @Test
    fun 존재하지_않는_정산_상세_조회_시_예외가_발생한다() {
        whenever(settlementRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { settlementQueryServiceImpl.getSettlementDetail(999L, 1L) }
            .isInstanceOf(SettlementNotFoundException::class.java)
    }
}
