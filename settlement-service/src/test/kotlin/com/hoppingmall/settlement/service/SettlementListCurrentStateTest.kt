package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.enums.SettlementStatus
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
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@DisplayName("정산 목록의 현재 상태")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementListCurrentStateTest {

    @Autowired
    private lateinit var settlementCommandService: SettlementCommandService

    @Autowired
    private lateinit var settlementQueryService: SettlementQueryService

    @Autowired
    private lateinit var settlementSnapshotService: SettlementSnapshotService

    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Autowired
    private lateinit var settlementItemRepository: SettlementItemRepository

    @Autowired
    private lateinit var settlementSummaryRepository: SettlementSummaryRepository

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var refundQueryPort: RefundQueryPort

    @BeforeEach
    fun setUp() {
        clear()
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(
                listOf(
                    OrderItemInfo(
                        id = 10L,
                        orderId = 100L,
                        sellerId = SELLER_ID,
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

    @AfterEach
    fun tearDown() {
        clear()
    }

    @Test
    fun 요약이_있어도_새로_만든_정산이_전체_목록에_보인다() {
        val january = create(SELLER_ID, date(1, 1), date(1, 31))
        settlementSnapshotService.createSnapshot()

        val february = create(SELLER_ID, date(2, 1), date(2, 28))

        assertThat(listAll().map { it.id }).containsExactlyInAnyOrder(january.id, february.id)
    }

    @Test
    fun 요약이_있어도_새로_만든_정산이_판매자_목록에_보인다() {
        val january = create(SELLER_ID, date(1, 1), date(1, 31))
        settlementSnapshotService.createSnapshot()

        val february = create(SELLER_ID, date(2, 1), date(2, 28))

        val result = settlementQueryService.getSettlements(SELLER_ID, null, PAGE).content
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(january.id, february.id)
    }

    @Test
    fun 요약이_있어도_확정한_정산의_상태가_목록에_보인다() {
        val january = create(SELLER_ID, date(1, 1), date(1, 31))
        settlementSnapshotService.createSnapshot()

        settlementCommandService.confirmSettlement(january.id)

        val listed = listAll().single { it.id == january.id }
        assertThat(listed.status).isEqualTo(SettlementStatus.CONFIRMED)
        assertThat(listed.confirmedAt).isNotNull()
    }

    @Test
    fun 요약이_있어도_지급한_정산의_상태가_목록에_보인다() {
        val january = create(SELLER_ID, date(1, 1), date(1, 31))
        settlementCommandService.confirmSettlement(january.id)
        settlementSnapshotService.createSnapshot()

        settlementCommandService.paySettlement(january.id)

        val listed = listAll().single { it.id == january.id }
        assertThat(listed.status).isEqualTo(SettlementStatus.PAID)
        assertThat(listed.paidAt).isNotNull()
    }

    @Test
    fun 요약이_있어도_상태로_거른_목록에_바뀐_상태가_반영된다() {
        val january = create(SELLER_ID, date(1, 1), date(1, 31))
        val february = create(SELLER_ID, date(2, 1), date(2, 28))
        settlementSnapshotService.createSnapshot()

        settlementCommandService.confirmSettlement(january.id)

        val confirmed = settlementQueryService.getSettlements(null, SettlementStatus.CONFIRMED, PAGE).content
        val calculated = settlementQueryService.getSettlements(SELLER_ID, SettlementStatus.CALCULATED, PAGE).content
        assertThat(confirmed.map { it.id }).containsExactly(january.id)
        assertThat(calculated.map { it.id }).containsExactly(february.id)
    }

    @Test
    fun 요약이_비어_있으면_목록은_모든_정산을_보여_준다() {
        val january = create(SELLER_ID, date(1, 1), date(1, 31))
        val february = create(OTHER_SELLER_ID, date(1, 1), date(1, 31))

        settlementCommandService.confirmSettlement(january.id)

        val result = listAll()
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(january.id, february.id)
        assertThat(result.single { it.id == january.id }.status).isEqualTo(SettlementStatus.CONFIRMED)
    }

    @Test
    fun 스냅샷을_다시_돌려도_목록의_정산과_상태가_같다() {
        val january = create(SELLER_ID, date(1, 1), date(1, 31))
        settlementSnapshotService.createSnapshot()
        val february = create(SELLER_ID, date(2, 1), date(2, 28))
        settlementCommandService.confirmSettlement(february.id)

        settlementSnapshotService.createSnapshot()

        val result = listAll()
        assertThat(result.map { it.id to it.status }).containsExactlyInAnyOrder(
            january.id to SettlementStatus.CALCULATED,
            february.id to SettlementStatus.CONFIRMED
        )
        assertThat(settlementSummaryRepository.count()).isEqualTo(2)
    }

    private fun create(sellerId: Long, start: LocalDate, end: LocalDate): SettlementResponse =
        settlementCommandService.createSettlement(
            CreateSettlementRequest(
                sellerId = sellerId,
                periodStart = start,
                periodEnd = end,
                commissionRate = BigDecimal("0.05")
            )
        )

    private fun listAll(): List<SettlementResponse> =
        settlementQueryService.getSettlements(null, null, PAGE).content

    private fun clear() {
        settlementSummaryRepository.deleteAllInBatch()
        settlementItemRepository.deleteAllInBatch()
        settlementRepository.deleteAllInBatch()
    }

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    companion object {
        private const val SELLER_ID = 1L
        private const val OTHER_SELLER_ID = 2L
        private val PAGE = PageRequest.of(0, 20)
    }
}
