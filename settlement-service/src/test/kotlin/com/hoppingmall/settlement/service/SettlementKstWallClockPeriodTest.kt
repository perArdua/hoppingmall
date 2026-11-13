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
import org.junit.jupiter.api.AfterEach
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
import java.util.TimeZone

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("정산 기간을 한국 벽시계 [그날 00:00, 다음 날 00:00)으로 order에 넘긴다")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementKstWallClockPeriodTest {

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

    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    private val lastMomentItem = orderItem(10L)
    private val midnightItem = orderItem(20L)
    private val completedAtKst = mapOf(
        lastMomentItem to LocalDateTime.of(2026, 11, 14, 23, 59, 59, 500_000_000),
        midnightItem to LocalDateTime.of(2026, 11, 15, 0, 0, 0)
    )

    @BeforeEach
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        whenever(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        whenever(settlementRepository.save(any<Settlement>())).thenAnswer { invocation ->
            invocation.getArgument<Settlement>(0).also { ReflectionTestUtils.setField(it, "id", 1L) }
        }
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(emptyList())
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenAnswer { invocation ->
                val start = invocation.getArgument<LocalDateTime>(1)
                val end = invocation.getArgument<LocalDateTime>(2)
                completedAtKst.filterValues { !it.isBefore(start) && it.isBefore(end) }.keys.toList()
            }
    }

    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun 한국_23시_59분_59초_5에_완료된_주문은_그날_정산에만_잡힌다() {
        val sameDay = settledOrderItemIds(LocalDate.of(2026, 11, 14))
        val nextDay = settledOrderItemIds(LocalDate.of(2026, 11, 15))

        assertThat(sameDay).containsExactly(lastMomentItem.id)
        assertThat(nextDay).doesNotContain(lastMomentItem.id)
    }

    @Test
    fun 다음_날_한국_00시_정각에_완료된_주문은_다음_날_정산에만_잡힌다() {
        val sameDay = settledOrderItemIds(LocalDate.of(2026, 11, 14))
        val nextDay = settledOrderItemIds(LocalDate.of(2026, 11, 15))

        assertThat(sameDay).doesNotContain(midnightItem.id)
        assertThat(nextDay).containsExactly(midnightItem.id)
    }

    @Test
    fun 앱_시간대가_UTC여도_판매와_환불_조회에_한국_날짜의_자정을_그대로_넘긴다() {
        settledOrderItemIds(LocalDate.of(2026, 11, 14))

        val start = LocalDateTime.of(2026, 11, 14, 0, 0)
        val end = LocalDateTime.of(2026, 11, 15, 0, 0)
        verify(orderItemQueryPort).findDeliveredItemsBySellerAndPeriod(eq(SELLER_ID), eq(start), eq(end))
        verify(refundQueryPort).findCompletedBySellerAndPeriod(eq(SELLER_ID), eq(start), eq(end))
    }

    private fun settledOrderItemIds(date: LocalDate): List<Long> {
        val saved = mutableListOf<Long>()
        whenever(settlementItemRepository.saveAll(any<List<SettlementItem>>())).thenAnswer { invocation ->
            invocation.getArgument<List<SettlementItem>>(0).also { items -> saved.addAll(items.map { it.orderItemId }) }
        }
        val service = SettlementCommandServiceImpl(
            settlementRepository,
            settlementItemRepository,
            orderItemQueryPort,
            refundQueryPort,
            transactionManager
        )
        try {
            service.createSettlement(
                CreateSettlementRequest(
                    sellerId = SELLER_ID,
                    periodStart = date,
                    periodEnd = date,
                    commissionRate = BigDecimal("0.1")
                )
            )
        } catch (e: SettlementNoSalesDataException) {
            return emptyList()
        }
        return saved
    }

    private fun orderItem(id: Long) = OrderItemInfo(
        id = id,
        orderId = id * 10,
        sellerId = SELLER_ID,
        productId = 1L,
        productName = "상품$id",
        productPrice = BigDecimal("3000"),
        quantity = 1,
        totalPrice = BigDecimal("3000")
    )

    companion object {
        private const val SELLER_ID = 7L
    }
}
