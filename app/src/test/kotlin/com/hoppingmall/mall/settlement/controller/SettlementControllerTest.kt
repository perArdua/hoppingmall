package com.hoppingmall.mall.settlement.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.settlement.domain.Settlement
import com.hoppingmall.mall.settlement.domain.SettlementItem
import com.hoppingmall.mall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.mall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.mall.settlement.dto.response.SettlementResponse
import com.hoppingmall.mall.settlement.enum.SettlementStatus
import com.hoppingmall.mall.settlement.service.SettlementCommandService
import com.hoppingmall.mall.settlement.service.SettlementQueryService
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.api.SellerInfo
import com.hoppingmall.mall.user.api.SellerQueryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("SettlementController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SettlementControllerTest {

    private val settlementCommandService: SettlementCommandService = mock()
    private val settlementQueryService: SettlementQueryService = mock()
    private val sellerQueryPort: SellerQueryPort = mock()
    private val controller = SettlementController(
        settlementCommandService, settlementQueryService, sellerQueryPort
    )

    private val adminPrincipal = UserPrincipal(1L, "admin@example.com", "ADMIN")
    private val sellerPrincipal = UserPrincipal(2L, "seller@example.com", "SELLER")

    private fun createSettlementResponse(): SettlementResponse {
        val settlement = Settlement.fixture(sellerId = 1L).withId(1L)
        return SettlementResponse.from(settlement)
    }

    @Nested
    @DisplayName("createSettlement")
    inner class CreateSettlement {

        @Test
        fun 정산을_생성한다() {
            val request = CreateSettlementRequest(
                sellerId = 1L,
                periodStart = LocalDate.of(2026, 3, 1),
                periodEnd = LocalDate.of(2026, 3, 31),
                commissionRate = BigDecimal("0.10")
            )
            whenever(settlementCommandService.createSettlement(any())).thenReturn(createSettlementResponse())

            val response = controller.createSettlement(request)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
        }
    }

    @Nested
    @DisplayName("confirmSettlement")
    inner class ConfirmSettlement {

        @Test
        fun 정산을_확정한다() {
            whenever(settlementCommandService.confirmSettlement(1L)).thenReturn(createSettlementResponse())

            val response = controller.confirmSettlement(1L)

            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }

    @Nested
    @DisplayName("paySettlement")
    inner class PaySettlement {

        @Test
        fun 정산을_지급_처리한다() {
            whenever(settlementCommandService.paySettlement(1L)).thenReturn(createSettlementResponse())

            val response = controller.paySettlement(1L)

            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }

    @Nested
    @DisplayName("getSettlementsForAdmin")
    inner class GetSettlementsForAdmin {

        @Test
        fun 관리자가_정산_목록을_조회한다() {
            val pageable = PageRequest.of(0, 20)
            whenever(settlementQueryService.getSettlements(eq(null), eq(null), any()))
                .thenReturn(PageImpl(listOf(createSettlementResponse())))

            val response = controller.getSettlementsForAdmin(null, null, pageable)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(1, response.body?.data?.totalElements)
        }
    }

    @Nested
    @DisplayName("getSettlementsForSeller")
    inner class GetSettlementsForSeller {

        @Test
        fun 판매자가_본인_정산_목록을_조회한다() {
            whenever(sellerQueryPort.findByUserId(2L)).thenReturn(SellerInfo(id = 10L, userId = 2L))

            val pageable = PageRequest.of(0, 20)
            whenever(settlementQueryService.getSettlements(eq(10L), eq(null), any()))
                .thenReturn(PageImpl(listOf(createSettlementResponse())))

            val response = controller.getSettlementsForSeller(sellerPrincipal, null, pageable)

            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }

    @Nested
    @DisplayName("getSettlementDetailForSeller")
    inner class GetSettlementDetailForSeller {

        @Test
        fun 판매자가_정산_상세를_조회한다() {
            whenever(sellerQueryPort.findByUserId(2L)).thenReturn(SellerInfo(id = 10L, userId = 2L))

            val settlement = Settlement.fixture(sellerId = 10L).withId(1L)
            val item = SettlementItem.fixture(settlementId = 1L).withId(1L)
            val detail = SettlementDetailResponse.from(settlement, listOf(item))
            whenever(settlementQueryService.getSettlementDetail(eq(1L), eq(10L))).thenReturn(detail)

            val response = controller.getSettlementDetailForSeller(sellerPrincipal, 1L)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(1, response.body?.data?.items?.size)
        }
    }
}
