package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.exception.SettlementAlreadyExistsException
import com.hoppingmall.settlement.port.OrderItemInfo
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@DisplayName("정산 기간 겹침")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementPeriodOverlapTest {

    @Autowired
    private lateinit var settlementCommandService: SettlementCommandService

    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Autowired
    private lateinit var settlementItemRepository: SettlementItemRepository

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var refundQueryPort: RefundQueryPort

    @BeforeEach
    fun setUp() {
        clear()
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenAnswer { invocation ->
                listOf(
                    OrderItemInfo(
                        id = invocation.getArgument<Long>(0) * 1000 + invocation.getArgument<LocalDateTime>(1).dayOfYear,
                        orderId = 100L,
                        sellerId = SELLER_ID,
                        productId = 1L,
                        productName = "상품A",
                        productPrice = BigDecimal("50000"),
                        quantity = 2,
                        totalPrice = BigDecimal("100000")
                    )
                )
            }
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() {
        clear()
    }

    @Test
    fun 기간이_뒤쪽으로_겹치는_정산은_만들_수_없다() {
        create(SELLER_ID, date(1, 1), date(1, 31))

        assertThatThrownBy { create(SELLER_ID, date(1, 15), date(2, 15)) }
            .isInstanceOf(SettlementAlreadyExistsException::class.java)
        assertThat(periodsOf(SELLER_ID)).containsExactly(date(1, 1) to date(1, 31))
    }

    @Test
    fun 기간이_앞쪽으로_겹치는_정산은_만들_수_없다() {
        create(SELLER_ID, date(1, 15), date(2, 15))

        assertThatThrownBy { create(SELLER_ID, date(1, 1), date(1, 31)) }
            .isInstanceOf(SettlementAlreadyExistsException::class.java)
        assertThat(periodsOf(SELLER_ID)).containsExactly(date(1, 15) to date(2, 15))
    }

    @Test
    fun 기존_기간을_감싸는_정산은_만들_수_없다() {
        create(SELLER_ID, date(1, 10), date(1, 20))

        assertThatThrownBy { create(SELLER_ID, date(1, 1), date(1, 31)) }
            .isInstanceOf(SettlementAlreadyExistsException::class.java)
        assertThat(periodsOf(SELLER_ID)).containsExactly(date(1, 10) to date(1, 20))
    }

    @Test
    fun 종료일_하루만_겹쳐도_만들_수_없다() {
        create(SELLER_ID, date(1, 1), date(1, 31))

        assertThatThrownBy { create(SELLER_ID, date(1, 31), date(2, 28)) }
            .isInstanceOf(SettlementAlreadyExistsException::class.java)
        assertThat(periodsOf(SELLER_ID)).containsExactly(date(1, 1) to date(1, 31))
    }

    @Test
    fun 끝과_시작이_맞닿은_기간은_만들_수_있다() {
        create(SELLER_ID, date(1, 1), date(1, 31))
        create(SELLER_ID, date(2, 1), date(2, 28))

        assertThat(periodsOf(SELLER_ID)).containsExactlyInAnyOrder(
            date(1, 1) to date(1, 31),
            date(2, 1) to date(2, 28)
        )
    }

    @Test
    fun 다른_판매자는_같은_기간의_정산을_만들_수_있다() {
        create(SELLER_ID, date(1, 1), date(1, 31))
        create(OTHER_SELLER_ID, date(1, 15), date(2, 15))

        assertThat(periodsOf(SELLER_ID)).containsExactly(date(1, 1) to date(1, 31))
        assertThat(periodsOf(OTHER_SELLER_ID)).containsExactly(date(1, 15) to date(2, 15))
    }

    private fun create(sellerId: Long, start: LocalDate, end: LocalDate) {
        settlementCommandService.createSettlement(
            CreateSettlementRequest(
                sellerId = sellerId,
                periodStart = start,
                periodEnd = end,
                commissionRate = BigDecimal("0.05")
            )
        )
    }

    private fun periodsOf(sellerId: Long): List<Pair<LocalDate, LocalDate>> =
        settlementRepository.findAll()
            .filter { it.sellerId == sellerId }
            .map { it.periodStart to it.periodEnd }

    private fun clear() {
        settlementItemRepository.deleteAllInBatch()
        settlementRepository.deleteAllInBatch()
    }

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    companion object {
        private const val SELLER_ID = 1L
        private const val OTHER_SELLER_ID = 2L
    }
}
