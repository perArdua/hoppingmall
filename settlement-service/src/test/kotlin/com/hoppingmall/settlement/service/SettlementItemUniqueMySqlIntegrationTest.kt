package com.hoppingmall.settlement.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.settlement.config.JpaConfig
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.port.OrderItemInfo
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundQueryPort
import com.hoppingmall.settlement.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("정산 항목 주문 항목 유니크 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class, SettlementItemUniqueMySqlIntegrationTest.SettlementServices::class)
class SettlementItemUniqueMySqlIntegrationTest : MySqlJpaTestSupport() {

    @TestConfiguration
    @ComponentScan(basePackages = ["com.hoppingmall.settlement.service"])
    class SettlementServices

    @Autowired
    private lateinit var settlementCommandService: SettlementCommandService

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var refundQueryPort: RefundQueryPort

    @BeforeEach
    fun stubPorts() {
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(
                listOf(
                    OrderItemInfo(
                        id = ORDER_ITEM_ID,
                        orderId = 880L,
                        sellerId = 41L,
                        productId = 1L,
                        productName = "상품A",
                        productPrice = BigDecimal("50000"),
                        quantity = 2,
                        totalPrice = BigDecimal("100000")
                    )
                )
            )
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(emptyList())
    }

    @Test
    fun 이미_정산된_주문_항목이_다른_판매자의_정산에_들어가면_충돌로_거절되고_정산도_남지_않는다() {
        create(41L, date(1, 1), date(1, 31))

        val thrown = catchThrowable { create(42L, date(1, 1), date(1, 31)) }

        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        val errorCode = (thrown as BusinessException).errorCode
        assertThat(errorCode.code).isEqualTo("STL011")
        assertThat(errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(count("settlement_items")).isEqualTo(1)
        assertThat(count("settlements")).isEqualTo(1)
    }

    @Test
    fun 이미_정산된_주문_항목이_같은_판매자의_다음_기간_정산에_들어가면_충돌로_거절된다() {
        create(43L, date(1, 1), date(1, 31))

        val thrown = catchThrowable { create(43L, date(2, 1), date(2, 28)) }

        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        assertThat((thrown as BusinessException).errorCode.code).isEqualTo("STL011")
        assertThat(count("settlement_items")).isEqualTo(1)
        assertThat(count("settlements")).isEqualTo(1)
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

    private fun count(table: String): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java)!!

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    companion object {
        private const val ORDER_ITEM_ID = 88L
    }
}
