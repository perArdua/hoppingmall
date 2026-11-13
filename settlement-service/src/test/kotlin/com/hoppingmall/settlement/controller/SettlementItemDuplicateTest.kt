package com.hoppingmall.settlement.controller

import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import com.hoppingmall.settlement.port.OrderItemInfo
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundQueryPort
import com.hoppingmall.settlement.port.SellerInfo
import com.hoppingmall.settlement.port.SellerQueryPort
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("같은 주문 항목이 두 정산에 들어가는 정산 생성")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementItemDuplicateTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

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

    @MockitoBean
    private lateinit var sellerQueryPort: SellerQueryPort

    @BeforeEach
    fun setUp() {
        clear()
        listOf(SELLER_USER_ID, OTHER_SELLER_USER_ID).forEach { userId ->
            whenever(sellerQueryPort.findByUserId(userId))
                .thenReturn(SellerInfo(id = userId + 100, userId = userId, approved = true))
        }
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(
                listOf(
                    OrderItemInfo(
                        id = ORDER_ITEM_ID,
                        orderId = 770L,
                        sellerId = SELLER_USER_ID,
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
    fun 이미_정산된_주문_항목이_맞닿은_다음_기간_정산에_다시_들어가면_409이고_항목은_하나만_남는다() {
        create(SELLER_USER_ID, "2026-01-01", "2026-01-31").andExpect { status { isOk() } }

        create(SELLER_USER_ID, "2026-02-01", "2026-02-28").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("STL011") }
        }

        assertThat(settlementItemRepository.findAll().map { it.orderItemId }).containsExactly(ORDER_ITEM_ID)
        assertThat(settlementRepository.count()).isEqualTo(1)
    }

    @Test
    fun 이미_정산된_주문_항목이_다른_판매자의_정산에_들어가면_409이고_항목은_하나만_남는다() {
        create(SELLER_USER_ID, "2026-01-01", "2026-01-31").andExpect { status { isOk() } }

        create(OTHER_SELLER_USER_ID, "2026-01-01", "2026-01-31").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("STL011") }
        }

        assertThat(settlementItemRepository.findAll().map { it.orderItemId }).containsExactly(ORDER_ITEM_ID)
        assertThat(settlementRepository.findAll().map { it.sellerId }).containsExactly(SELLER_USER_ID)
    }

    @Test
    fun 같은_판매자의_겹치는_기간이면_주문_항목_유니크보다_기간_겹침_검사가_먼저_막는다() {
        create(SELLER_USER_ID, "2026-01-01", "2026-01-31").andExpect { status { isOk() } }

        create(SELLER_USER_ID, "2026-01-15", "2026-02-15").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("STL003") }
        }

        assertThat(settlementItemRepository.count()).isEqualTo(1)
        assertThat(settlementRepository.count()).isEqualTo(1)
    }

    private fun create(sellerId: Long, start: String, end: String) =
        mockMvc.post("/api/v1/admin/settlements") {
            header("X-User-Id", "1")
            header("x-user-role", "ADMIN")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"seller_id":$sellerId,"period_start":"$start","period_end":"$end","commission_rate":0.05}
            """.trimIndent()
        }

    private fun clear() {
        settlementSummaryRepository.deleteAllInBatch()
        settlementItemRepository.deleteAllInBatch()
        settlementRepository.deleteAllInBatch()
    }

    companion object {
        private const val SELLER_USER_ID = 10L
        private const val OTHER_SELLER_USER_ID = 20L
        private const val ORDER_ITEM_ID = 77L
    }
}
