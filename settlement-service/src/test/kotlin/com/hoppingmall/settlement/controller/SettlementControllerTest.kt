package com.hoppingmall.settlement.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.settlement.dto.response.SettlementItemResponse
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.enums.SettlementStatus
import com.hoppingmall.settlement.exception.SettlementSellerNotFoundException
import com.hoppingmall.settlement.port.SellerInfo
import com.hoppingmall.settlement.port.SellerQueryPort
import com.hoppingmall.settlement.service.SettlementCommandService
import com.hoppingmall.settlement.service.SettlementQueryService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("SettlementController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SettlementControllerTest {

    @Mock
    private lateinit var settlementCommandService: SettlementCommandService

    @Mock
    private lateinit var settlementQueryService: SettlementQueryService

    @Mock
    private lateinit var sellerQueryPort: SellerQueryPort

    private lateinit var controller: SettlementController

    @BeforeEach
    fun setUp() {
        controller = SettlementController(settlementCommandService, settlementQueryService, sellerQueryPort)
    }

    private fun createSettlementResponse(id: Long = 1L): SettlementResponse {
        return SettlementResponse(
            id = id,
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("1000000"),
            totalRefundAmount = BigDecimal("50000"),
            commissionRate = BigDecimal("0.1000"),
            commissionAmount = BigDecimal("95000"),
            settlementAmount = BigDecimal("855000"),
            status = SettlementStatus.CALCULATED,
            confirmedAt = null,
            paidAt = null,
            createdAt = LocalDateTime.now()
        )
    }

    @Test
    fun 정산을_생성한다() {
        val request = CreateSettlementRequest(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            commissionRate = BigDecimal("0.1000")
        )
        whenever(settlementCommandService.createSettlement(request)).thenReturn(createSettlementResponse())

        val result = controller.createSettlement(request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.id).isEqualTo(1L)
    }

    @Test
    fun 정산을_확정한다() {
        whenever(settlementCommandService.confirmSettlement(1L)).thenReturn(createSettlementResponse())

        val result = controller.confirmSettlement(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 정산을_지급_처리한다() {
        whenever(settlementCommandService.paySettlement(1L)).thenReturn(createSettlementResponse())

        val result = controller.paySettlement(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 관리자가_정산_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        whenever(settlementQueryService.getSettlements(null, null, pageable))
            .thenReturn(PageImpl(listOf(createSettlementResponse())))

        val result = controller.getSettlementsForAdmin(null, null, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.content).hasSize(1)
    }

    @Test
    fun 관리자가_정산_상세를_조회한다() {
        val detail = SettlementDetailResponse(
            id = 1L, sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1), periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("1000000"), totalRefundAmount = BigDecimal("50000"),
            commissionRate = BigDecimal("0.1000"), commissionAmount = BigDecimal("95000"),
            settlementAmount = BigDecimal("855000"), status = SettlementStatus.CALCULATED,
            confirmedAt = null, paidAt = null, createdAt = LocalDateTime.now(),
            items = emptyList()
        )
        whenever(settlementQueryService.getSettlementDetail(1L, null)).thenReturn(detail)

        val result = controller.getSettlementDetailForAdmin(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 판매자가_정산_목록을_조회한다() {
        val userPrincipal = UserPrincipal.of(10L, "SELLER")
        val pageable = PageRequest.of(0, 20)
        whenever(sellerQueryPort.findByUserId(10L)).thenReturn(SellerInfo(id = 5L, userId = 10L))
        whenever(settlementQueryService.getSettlements(5L, null, pageable))
            .thenReturn(PageImpl(listOf(createSettlementResponse())))

        val result = controller.getSettlementsForSeller(userPrincipal, null, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 판매자가_정산_상세를_조회한다() {
        val userPrincipal = UserPrincipal.of(10L, "SELLER")
        val detail = SettlementDetailResponse(
            id = 1L, sellerId = 5L,
            periodStart = LocalDate.of(2026, 1, 1), periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("1000000"), totalRefundAmount = BigDecimal("50000"),
            commissionRate = BigDecimal("0.1000"), commissionAmount = BigDecimal("95000"),
            settlementAmount = BigDecimal("855000"), status = SettlementStatus.CALCULATED,
            confirmedAt = null, paidAt = null, createdAt = LocalDateTime.now(),
            items = emptyList()
        )
        whenever(sellerQueryPort.findByUserId(10L)).thenReturn(SellerInfo(id = 5L, userId = 10L))
        whenever(settlementQueryService.getSettlementDetail(1L, 5L)).thenReturn(detail)

        val result = controller.getSettlementDetailForSeller(userPrincipal, 1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 판매자_정보가_없으면_목록_조회시_예외가_발생한다() {
        val userPrincipal = UserPrincipal.of(10L, "SELLER")
        val pageable = PageRequest.of(0, 20)
        whenever(sellerQueryPort.findByUserId(10L)).thenReturn(null)

        assertThatThrownBy { controller.getSettlementsForSeller(userPrincipal, null, pageable) }
            .isInstanceOf(SettlementSellerNotFoundException::class.java)
    }

    @Test
    fun 판매자_정보가_없으면_상세_조회시_예외가_발생한다() {
        val userPrincipal = UserPrincipal.of(10L, "SELLER")
        whenever(sellerQueryPort.findByUserId(10L)).thenReturn(null)

        assertThatThrownBy { controller.getSettlementDetailForSeller(userPrincipal, 1L) }
            .isInstanceOf(SettlementSellerNotFoundException::class.java)
    }
}
