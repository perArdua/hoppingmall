package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementItem
import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.settlement.port.OrderItemInfo
import com.hoppingmall.settlement.port.OrderItemQueryPort
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("정산 기간의 하루는 한국 날짜")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementKstDayBoundaryTest {

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

    private val kst = ZoneId.of("Asia/Seoul")

    private val deliveredItem = OrderItemInfo(
        id = 10L,
        orderId = 100L,
        sellerId = SELLER_ID,
        productId = 1L,
        productName = "상품A",
        productPrice = BigDecimal("3000"),
        quantity = 1,
        totalPrice = BigDecimal("3000")
    )

    @BeforeEach
    fun setUp() {
        whenever(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        whenever(settlementRepository.save(any<Settlement>())).thenAnswer { invocation ->
            invocation.getArgument<Settlement>(0).also { ReflectionTestUtils.setField(it, "id", 1L) }
        }
        whenever(settlementItemRepository.saveAll(any<List<SettlementItem>>())).thenReturn(emptyList())
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(emptyList())
    }

    @Test
    fun 한국_시각_00시_23분에_배송_완료된_주문은_그날_정산에_잡힌다() {
        val service = serviceIn(ZoneOffset.UTC)
        deliveredAt(ZonedDateTime.of(2026, 11, 14, 0, 23, 0, 0, kst), ZoneOffset.UTC)

        val result = service.createSettlement(requestFor(LocalDate.of(2026, 11, 14)))

        assertThat(result.totalSalesAmount).isEqualByComparingTo(BigDecimal("3000"))
    }

    @Test
    fun 한국_시각_00시_23분에_배송_완료된_주문은_전날_정산에_잡히지_않는다() {
        val service = serviceIn(ZoneOffset.UTC)
        deliveredAt(ZonedDateTime.of(2026, 11, 14, 0, 23, 0, 0, kst), ZoneOffset.UTC)

        assertThatThrownBy { service.createSettlement(requestFor(LocalDate.of(2026, 11, 13))) }
            .isInstanceOf(SettlementNoSalesDataException::class.java)
    }

    @Test
    fun 한국_시각_23시_59분에_배송_완료된_주문은_다음날_정산에_잡히지_않는다() {
        val service = serviceIn(ZoneOffset.UTC)
        deliveredAt(ZonedDateTime.of(2026, 11, 13, 23, 59, 0, 0, kst), ZoneOffset.UTC)

        assertThatThrownBy { service.createSettlement(requestFor(LocalDate.of(2026, 11, 14))) }
            .isInstanceOf(SettlementNoSalesDataException::class.java)
    }

    @Test
    fun 앱_시간대가_UTC이면_한국_자정을_UTC_시각으로_바꿔_판매와_환불_조회에_넘긴다() {
        val service = serviceIn(ZoneOffset.UTC)
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(listOf(deliveredItem))

        service.createSettlement(requestFor(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15)))

        val expectedStart = LocalDateTime.of(2026, 11, 13, 15, 0)
        val expectedEnd = LocalDateTime.of(2026, 11, 15, 15, 0)
        verify(orderItemQueryPort).findDeliveredItemsBySellerAndPeriod(eq(SELLER_ID), eq(expectedStart), eq(expectedEnd))
        verify(refundQueryPort).findCompletedBySellerAndPeriod(eq(SELLER_ID), eq(expectedStart), eq(expectedEnd))
    }

    @Test
    fun 앱_시간대가_한국이면_날짜의_자정을_그대로_넘긴다() {
        val service = serviceIn(kst)
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(listOf(deliveredItem))

        service.createSettlement(requestFor(LocalDate.of(2026, 11, 14)))

        val start = argumentCaptor<LocalDateTime>()
        val end = argumentCaptor<LocalDateTime>()
        verify(orderItemQueryPort).findDeliveredItemsBySellerAndPeriod(eq(SELLER_ID), start.capture(), end.capture())
        assertThat(start.firstValue).isEqualTo(LocalDateTime.of(2026, 11, 14, 0, 0))
        assertThat(end.firstValue).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0))
    }

    private fun serviceIn(applicationZone: ZoneId) = SettlementCommandServiceImpl(
        settlementRepository,
        settlementItemRepository,
        orderItemQueryPort,
        refundQueryPort,
        transactionManager,
        applicationZone = applicationZone
    )

    private fun deliveredAt(deliveredAt: ZonedDateTime, applicationZone: ZoneId) {
        val stored = deliveredAt.withZoneSameInstant(applicationZone).toLocalDateTime()
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenAnswer { invocation ->
                val start = invocation.getArgument<LocalDateTime>(1)
                val end = invocation.getArgument<LocalDateTime>(2)
                if (!stored.isBefore(start) && stored.isBefore(end)) listOf(deliveredItem) else emptyList<OrderItemInfo>()
            }
    }

    private fun requestFor(start: LocalDate, end: LocalDate = start) = CreateSettlementRequest(
        sellerId = SELLER_ID,
        periodStart = start,
        periodEnd = end,
        commissionRate = BigDecimal("0.1")
    )

    companion object {
        private const val SELLER_ID = 7L
    }
}
