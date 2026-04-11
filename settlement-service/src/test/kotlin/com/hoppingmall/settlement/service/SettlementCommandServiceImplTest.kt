package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementItem
import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.exception.SettlementAlreadyExistsException
import com.hoppingmall.settlement.exception.SettlementInvalidPeriodException
import com.hoppingmall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.settlement.exception.SettlementNotFoundException
import com.hoppingmall.settlement.port.OrderItemInfo
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundInfo
import com.hoppingmall.settlement.port.RefundQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementCommandServiceImplTest {

    @Mock
    private lateinit var settlementRepository: SettlementRepository

    @Mock
    private lateinit var settlementItemRepository: SettlementItemRepository

    @Mock
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @Mock
    private lateinit var refundQueryPort: RefundQueryPort

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var settlementCommandServiceImpl: SettlementCommandServiceImpl

    @BeforeEach
    fun setUp() {
        whenever(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        settlementCommandServiceImpl = SettlementCommandServiceImpl(
            settlementRepository, settlementItemRepository, orderItemQueryPort, refundQueryPort, transactionManager
        )
    }

    @Test
    fun 정산_생성_시_매출과_환불을_조회하고_정산을_생성한다() {
        val request = CreateSettlementRequest(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            commissionRate = BigDecimal("0.05")
        )

        val orderItems = listOf(
            OrderItemInfo(
                id = 10L,
                orderId = 100L,
                sellerId = 1L,
                productId = 1L,
                productName = "상품A",
                productPrice = BigDecimal("50000"),
                quantity = 2,
                totalPrice = BigDecimal("100000")
            )
        )

        val refunds = listOf(
            RefundInfo(id = 20L, refundAmount = BigDecimal("10000"))
        )

        val settlement = Settlement.create(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("100000"),
            totalRefundAmount = BigDecimal("10000"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("5000.00"),
            settlementAmount = BigDecimal("85000.00")
        )
        ReflectionTestUtils.setField(settlement, "id", 1L)

        whenever(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(any(), any(), any())).thenReturn(false)
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>())).thenReturn(orderItems)
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>())).thenReturn(refunds)
        whenever(settlementRepository.save(any<Settlement>())).thenReturn(settlement)
        whenever(settlementItemRepository.saveAll(any<List<SettlementItem>>())).thenReturn(emptyList())

        val result = settlementCommandServiceImpl.createSettlement(request)

        assertThat(result.sellerId).isEqualTo(1L)
        assertThat(result.totalSalesAmount).isEqualByComparingTo(BigDecimal("100000"))
        assertThat(result.totalRefundAmount).isEqualByComparingTo(BigDecimal("10000"))
        verify(settlementRepository).save(any<Settlement>())
        verify(settlementItemRepository).saveAll(any<List<SettlementItem>>())
    }

    @Test
    fun 정산_기간이_유효하지_않으면_예외가_발생한다() {
        val request = CreateSettlementRequest(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 31),
            periodEnd = LocalDate.of(2026, 1, 1),
            commissionRate = BigDecimal("0.05")
        )

        assertThatThrownBy { settlementCommandServiceImpl.createSettlement(request) }
            .isInstanceOf(SettlementInvalidPeriodException::class.java)
    }

    @Test
    fun 이미_존재하는_정산이면_예외가_발생한다() {
        val request = CreateSettlementRequest(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            commissionRate = BigDecimal("0.05")
        )

        val orderItems = listOf(
            OrderItemInfo(
                id = 10L,
                orderId = 100L,
                sellerId = 1L,
                productId = 1L,
                productName = "상품A",
                productPrice = BigDecimal("50000"),
                quantity = 2,
                totalPrice = BigDecimal("100000")
            )
        )

        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>())).thenReturn(orderItems)
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>())).thenReturn(emptyList())
        whenever(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(any(), any(), any())).thenReturn(true)

        assertThatThrownBy { settlementCommandServiceImpl.createSettlement(request) }
            .isInstanceOf(SettlementAlreadyExistsException::class.java)
    }

    @Test
    fun 매출_데이터가_없으면_예외가_발생한다() {
        val request = CreateSettlementRequest(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            commissionRate = BigDecimal("0.05")
        )

        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>())).thenReturn(emptyList())

        assertThatThrownBy { settlementCommandServiceImpl.createSettlement(request) }
            .isInstanceOf(SettlementNoSalesDataException::class.java)
    }

    @Test
    fun 정산_확인_시_상태가_변경된다() {
        val settlement = Settlement.create(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("100000"),
            totalRefundAmount = BigDecimal("0"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("5000.00"),
            settlementAmount = BigDecimal("95000.00")
        )
        ReflectionTestUtils.setField(settlement, "id", 1L)

        whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

        val result = settlementCommandServiceImpl.confirmSettlement(1L)

        assertThat(result.status.name).isEqualTo("CONFIRMED")
    }

    @Test
    fun 정산_지급_시_상태가_변경된다() {
        val settlement = Settlement.create(
            sellerId = 1L,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("100000"),
            totalRefundAmount = BigDecimal("0"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("5000.00"),
            settlementAmount = BigDecimal("95000.00")
        )
        ReflectionTestUtils.setField(settlement, "id", 1L)
        settlement.confirm()

        whenever(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement))

        val result = settlementCommandServiceImpl.paySettlement(1L)

        assertThat(result.status.name).isEqualTo("PAID")
    }

    @Test
    fun 존재하지_않는_정산_확인_시_예외가_발생한다() {
        whenever(settlementRepository.findById(any())).thenReturn(Optional.empty())

        assertThatThrownBy { settlementCommandServiceImpl.confirmSettlement(999L) }
            .isInstanceOf(SettlementNotFoundException::class.java)
    }
}
