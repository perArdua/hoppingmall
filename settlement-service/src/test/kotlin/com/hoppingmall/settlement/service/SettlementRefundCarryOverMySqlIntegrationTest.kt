package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.config.JpaConfig
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.settlement.port.OrderItemInfo
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundInfo
import com.hoppingmall.settlement.port.RefundQueryPort
import com.hoppingmall.settlement.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("정산의 환불 차감·음수 이월·순매출 수수료 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class, SettlementConcurrentCreationMySqlIntegrationTest.SettlementServices::class)
class SettlementRefundCarryOverMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var settlementCommandService: SettlementCommandService

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var refundQueryPort: RefundQueryPort

    private val sales = mutableMapOf<Int, List<OrderItemInfo>>()
    private val refunds = mutableMapOf<Int, List<RefundInfo>>()

    @BeforeEach
    fun stubPorts() {
        sales.clear()
        refunds.clear()
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenAnswer { invocation -> sales[invocation.getArgument<LocalDateTime>(1).monthValue].orEmpty() }
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenAnswer { invocation -> refunds[invocation.getArgument<LocalDateTime>(1).monthValue].orEmpty() }
    }

    @Test
    fun 매출이_없는_기간에_완료된_환불도_정산되고_모자란_금액은_다음_정산에서_뺀다() {
        sales[1] = listOf(sale(101L, "100000"))
        refunds[2] = listOf(RefundInfo(id = 901L, refundAmount = BigDecimal("30000")))
        sales[3] = listOf(sale(102L, "100000"))

        val january = create(1)
        val february = create(2)
        val march = create(3)

        assertThat(january.settlementAmount).isEqualByComparingTo("95000")
        assertThat(february.totalSalesAmount).isEqualByComparingTo("0")
        assertThat(february.totalRefundAmount).isEqualByComparingTo("30000")
        assertThat(february.settlementAmount).isEqualByComparingTo("0")
        assertThat(march.settlementAmount).isEqualByComparingTo("66500")
        assertThat(paidOut()).isEqualByComparingTo("161500")
    }

    @Test
    fun 환불이_매출보다_크면_지급액은_0이고_모자란_금액은_다음_정산에서_뺀다() {
        sales[1] = listOf(sale(201L, "10000"))
        refunds[1] = listOf(RefundInfo(id = 902L, refundAmount = BigDecimal("30000")))
        sales[2] = listOf(sale(202L, "100000"))

        val january = create(1)
        val february = create(2)

        assertThat(january.settlementAmount).isEqualByComparingTo("0")
        assertThat(february.settlementAmount).isEqualByComparingTo("76000")
        assertThat(minSettlementAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun 수수료는_환불을_뺀_순매출에_매긴다() {
        sales[1] = listOf(sale(301L, "100000"))
        refunds[1] = listOf(RefundInfo(id = 903L, refundAmount = BigDecimal("20000")))

        val january = create(1)

        assertThat(january.commissionAmount).isEqualByComparingTo("4000")
        assertThat(january.settlementAmount).isEqualByComparingTo("76000")
    }

    @Test
    fun 같은_환불이_두_기간에_잡혀도_한_번만_차감된다() {
        sales[1] = listOf(sale(401L, "100000"))
        refunds[1] = listOf(RefundInfo(id = 904L, refundAmount = BigDecimal("20000")))
        sales[2] = listOf(sale(402L, "50000"))
        refunds[2] = listOf(RefundInfo(id = 904L, refundAmount = BigDecimal("20000")))

        create(1)
        val february = create(2)

        assertThat(february.totalRefundAmount).isEqualByComparingTo("0")
        assertThat(february.settlementAmount).isEqualByComparingTo("47500")
        assertThat(totalRefundDeducted()).isEqualByComparingTo("20000")
    }

    @Test
    fun 매출도_차감할_환불도_없는_기간은_정산을_만들지_않는다() {
        sales[1] = listOf(sale(501L, "100000"))
        refunds[1] = listOf(RefundInfo(id = 905L, refundAmount = BigDecimal("20000")))
        refunds[2] = listOf(RefundInfo(id = 905L, refundAmount = BigDecimal("20000")))
        create(1)

        assertThatThrownBy { create(2) }.isInstanceOf(SettlementNoSalesDataException::class.java)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlements", Long::class.java)).isEqualTo(1L)
    }

    private fun create(month: Int): SettlementResponse {
        val start = LocalDate.of(2026, month, 1)
        return settlementCommandService.createSettlement(
            CreateSettlementRequest(
                sellerId = SELLER_ID,
                periodStart = start,
                periodEnd = start.withDayOfMonth(start.lengthOfMonth()),
                commissionRate = BigDecimal("0.05")
            )
        )
    }

    private fun sale(orderItemId: Long, amount: String) = OrderItemInfo(
        id = orderItemId,
        orderId = orderItemId * 10,
        sellerId = SELLER_ID,
        productId = 1L,
        productName = "상품$orderItemId",
        productPrice = BigDecimal(amount),
        quantity = 1,
        totalPrice = BigDecimal(amount)
    )

    private fun paidOut(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT SUM(settlement_amount) FROM settlements", BigDecimal::class.java)!!

    private fun minSettlementAmount(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT MIN(settlement_amount) FROM settlements", BigDecimal::class.java)!!

    private fun totalRefundDeducted(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT SUM(total_refund_amount) FROM settlements", BigDecimal::class.java)!!

    companion object {
        private const val SELLER_ID = 61L
    }
}
