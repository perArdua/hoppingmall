package com.hoppingmall.mall.settlement.service

import com.hoppingmall.mall.order.api.OrderItemInfo
import com.hoppingmall.mall.order.api.OrderItemQueryPort
import com.hoppingmall.mall.refund.api.RefundInfo
import com.hoppingmall.mall.refund.api.RefundQueryPort
import com.hoppingmall.mall.settlement.domain.Settlement
import com.hoppingmall.mall.settlement.domain.SettlementItem
import com.hoppingmall.mall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.mall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.mall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.mall.settlement.enum.SettlementStatus
import com.hoppingmall.mall.settlement.exception.SettlementAlreadyExistsException
import com.hoppingmall.mall.settlement.exception.SettlementInvalidPeriodException
import com.hoppingmall.mall.settlement.exception.SettlementInvalidStatusException
import com.hoppingmall.mall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.mall.settlement.exception.SettlementNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@DisplayName("SettlementCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SettlementCommandServiceImplTest {

    private val settlementRepository: SettlementRepository = mock()
    private val settlementItemRepository: SettlementItemRepository = mock()
    private val orderItemQueryPort: OrderItemQueryPort = mock()
    private val refundQueryPort: RefundQueryPort = mock()
    private val service = SettlementCommandServiceImpl(
        settlementRepository, settlementItemRepository, orderItemQueryPort, refundQueryPort
    )

    @Nested
    @DisplayName("createSettlement")
    inner class CreateSettlement {

        private val request = CreateSettlementRequest(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 3, 1),
            periodEnd = LocalDate.of(2026, 3, 31),
            commissionRate = BigDecimal("0.10")
        )

        @Test
        fun 정산을_생성한다() {
            whenever(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                request.sellerId, request.periodStart, request.periodEnd
            )).thenReturn(false)

            val orderItemInfo = OrderItemInfo(
                id = 1L, orderId = 1L, sellerId = 1L, productId = 10L,
                productName = "테스트 상품", productPrice = BigDecimal("50000"),
                quantity = 2, totalPrice = BigDecimal("100000")
            )

            whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(
                any(), any(), any()
            )).thenReturn(listOf(orderItemInfo))

            whenever(refundQueryPort.findCompletedBySellerAndPeriod(
                any(), any(), any()
            )).thenReturn(emptyList())

            whenever(settlementRepository.save(any<Settlement>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Settlement).withId(1L)
            }

            val result = service.createSettlement(request)

            assertEquals(1L, result.sellerId)
            assertEquals(BigDecimal("100000"), result.totalSalesAmount)
            assertEquals(BigDecimal.ZERO, result.totalRefundAmount)
            assertEquals(BigDecimal("10000.00"), result.commissionAmount)
            assertEquals(BigDecimal("90000.00"), result.settlementAmount)
            assertEquals(SettlementStatus.CALCULATED, result.status)
            verify(settlementItemRepository).saveAll(any<List<SettlementItem>>())
        }

        @Test
        fun 환불_금액을_차감하여_정산한다() {
            whenever(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                request.sellerId, request.periodStart, request.periodEnd
            )).thenReturn(false)

            val orderItemInfo = OrderItemInfo(
                id = 1L, orderId = 1L, sellerId = 1L, productId = 10L,
                productName = "테스트 상품", productPrice = BigDecimal("50000"),
                quantity = 2, totalPrice = BigDecimal("100000")
            )

            whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(
                any(), any(), any()
            )).thenReturn(listOf(orderItemInfo))

            val refundInfo = RefundInfo(
                id = 1L, refundAmount = BigDecimal("20000")
            )

            whenever(refundQueryPort.findCompletedBySellerAndPeriod(
                any(), any(), any()
            )).thenReturn(listOf(refundInfo))

            whenever(settlementRepository.save(any<Settlement>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Settlement).withId(1L)
            }

            val result = service.createSettlement(request)

            assertEquals(BigDecimal("100000"), result.totalSalesAmount)
            assertEquals(BigDecimal("20000"), result.totalRefundAmount)
            assertEquals(BigDecimal("10000.00"), result.commissionAmount)
            assertEquals(BigDecimal("70000.00"), result.settlementAmount)
        }

        @Test
        fun 기간이_잘못되면_예외가_발생한다() {
            val invalidRequest = request.copy(
                periodStart = LocalDate.of(2026, 3, 31),
                periodEnd = LocalDate.of(2026, 3, 1)
            )

            assertThrows<SettlementInvalidPeriodException> {
                service.createSettlement(invalidRequest)
            }
        }

        @Test
        fun 이미_존재하는_정산이면_예외가_발생한다() {
            whenever(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                request.sellerId, request.periodStart, request.periodEnd
            )).thenReturn(true)

            assertThrows<SettlementAlreadyExistsException> {
                service.createSettlement(request)
            }
        }

        @Test
        fun 매출_데이터가_없으면_예외가_발생한다() {
            whenever(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                request.sellerId, request.periodStart, request.periodEnd
            )).thenReturn(false)

            whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(
                any(), any(), any()
            )).thenReturn(emptyList())

            assertThrows<SettlementNoSalesDataException> {
                service.createSettlement(request)
            }
        }
    }

    @Nested
    @DisplayName("confirmSettlement")
    inner class ConfirmSettlement {

        @Test
        fun 정산을_확정한다() {
            val settlement = Settlement.fixture().withId(1L)
            whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

            val result = service.confirmSettlement(1L)

            assertEquals(SettlementStatus.CONFIRMED, result.status)
        }

        @Test
        fun 존재하지_않는_정산이면_예외가_발생한다() {
            whenever(settlementRepository.findById(1L)).thenReturn(Optional.empty())

            assertThrows<SettlementNotFoundException> {
                service.confirmSettlement(1L)
            }
        }

        @Test
        fun 이미_확정된_정산은_다시_확정할_수_없다() {
            val settlement = Settlement.fixture().withId(1L)
            settlement.confirm()
            whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

            assertThrows<SettlementInvalidStatusException> {
                service.confirmSettlement(1L)
            }
        }
    }

    @Nested
    @DisplayName("paySettlement")
    inner class PaySettlement {

        @Test
        fun 정산_지급을_처리한다() {
            val settlement = Settlement.fixture().withId(1L)
            settlement.confirm()
            whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

            val result = service.paySettlement(1L)

            assertEquals(SettlementStatus.PAID, result.status)
        }

        @Test
        fun 확정되지_않은_정산은_지급할_수_없다() {
            val settlement = Settlement.fixture().withId(1L)
            whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

            assertThrows<SettlementInvalidStatusException> {
                service.paySettlement(1L)
            }
        }
    }
}
