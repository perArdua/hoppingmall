package com.hoppingmall.settlement.controller

import com.hoppingmall.settlement.domain.Settlement
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("정산의 판매자 id는 사용자 id")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementSellerUserIdTest {

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
        whenever(sellerQueryPort.findByUserId(SELLER_A_USER_ID))
            .thenReturn(SellerInfo(id = SELLER_A_ROW_ID, userId = SELLER_A_USER_ID, approved = true))
        whenever(sellerQueryPort.findByUserId(SELLER_B_USER_ID))
            .thenReturn(SellerInfo(id = SELLER_B_ROW_ID, userId = SELLER_B_USER_ID, approved = true))
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(
                listOf(
                    OrderItemInfo(
                        id = 70L,
                        orderId = 700L,
                        sellerId = SELLER_A_USER_ID,
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
    fun 판매자는_자기_사용자_id로_만든_정산을_목록에서_본다() {
        val settlement = saveSettlement(SELLER_A_USER_ID)

        mockMvc.get("/api/v1/seller/settlements") {
            header("X-User-Id", SELLER_A_USER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(1) }
            jsonPath("$.data.content[0].id") { value(settlement.id!!.toInt()) }
        }
    }

    @Test
    fun 판매자는_자기_사용자_id로_만든_정산의_상세를_본다() {
        val settlement = saveSettlement(SELLER_A_USER_ID)

        mockMvc.get("/api/v1/seller/settlements/${settlement.id}") {
            header("X-User-Id", SELLER_A_USER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(settlement.id!!.toInt()) }
        }
    }

    @Test
    fun 판매자_행_id가_다른_판매자의_사용자_id와_같아도_그_판매자의_정산_목록을_보지_못한다() {
        saveSettlement(SELLER_A_USER_ID)

        mockMvc.get("/api/v1/seller/settlements") {
            header("X-User-Id", SELLER_B_USER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(0) }
        }
    }

    @Test
    fun 판매자_행_id가_다른_판매자의_사용자_id와_같아도_그_판매자의_정산_상세는_403이다() {
        val settlement = saveSettlement(SELLER_A_USER_ID)

        mockMvc.get("/api/v1/seller/settlements/${settlement.id}") {
            header("X-User-Id", SELLER_B_USER_ID.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("STL005") }
        }
    }

    @Test
    fun 관리자_정산_생성의_sellerId가_판매자가_아닌_사용자면_400이고_정산을_만들지_않는다() {
        whenever(sellerQueryPort.findByUserId(NOT_SELLER_USER_ID)).thenReturn(null)

        createAsAdmin(NOT_SELLER_USER_ID).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("STL010") }
        }

        assertThat(settlementRepository.count()).isZero()
    }

    @Test
    fun 관리자_정산_생성의_sellerId가_승인되지_않은_판매자의_사용자_id면_400이고_정산을_만들지_않는다() {
        whenever(sellerQueryPort.findByUserId(PENDING_SELLER_USER_ID))
            .thenReturn(SellerInfo(id = 3L, userId = PENDING_SELLER_USER_ID, approved = false))

        createAsAdmin(PENDING_SELLER_USER_ID).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("STL010") }
        }

        assertThat(settlementRepository.count()).isZero()
    }

    @Test
    fun 관리자_정산_생성의_sellerId가_승인된_판매자의_사용자_id면_그_값을_판매자로_정산을_만든다() {
        createAsAdmin(SELLER_A_USER_ID).andExpect {
            status { isOk() }
            jsonPath("$.data.seller_id") { value(SELLER_A_USER_ID.toInt()) }
        }

        assertThat(settlementRepository.findAll().map { it.sellerId }).containsExactly(SELLER_A_USER_ID)
    }

    private fun createAsAdmin(sellerId: Long) =
        mockMvc.post("/api/v1/admin/settlements") {
            header("X-User-Id", "1")
            header("x-user-role", "ADMIN")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"seller_id":$sellerId,"period_start":"2026-01-01","period_end":"2026-01-31","commission_rate":0.05}
            """.trimIndent()
        }

    private fun saveSettlement(sellerId: Long): Settlement =
        settlementRepository.save(
            Settlement.create(
                sellerId = sellerId,
                periodStart = LocalDate.of(2026, 1, 1),
                periodEnd = LocalDate.of(2026, 1, 31),
                totalSalesAmount = BigDecimal("100000"),
                totalRefundAmount = BigDecimal.ZERO,
                commissionRate = BigDecimal("0.05"),
                commissionAmount = BigDecimal("5000"),
                settlementAmount = BigDecimal("95000")
            )
        )

    private fun clear() {
        settlementSummaryRepository.deleteAllInBatch()
        settlementItemRepository.deleteAllInBatch()
        settlementRepository.deleteAllInBatch()
    }

    companion object {
        private const val SELLER_A_USER_ID = 10L
        private const val SELLER_A_ROW_ID = 5L
        private const val SELLER_B_USER_ID = 20L
        private const val SELLER_B_ROW_ID = 10L
        private const val NOT_SELLER_USER_ID = 30L
        private const val PENDING_SELLER_USER_ID = 40L
    }
}
