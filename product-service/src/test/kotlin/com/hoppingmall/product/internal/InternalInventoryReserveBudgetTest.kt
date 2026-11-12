package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.exception.InventoryBudgetExceededException
import com.hoppingmall.product.inventory.exception.InventoryBusyException
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
import com.hoppingmall.product.inventory.service.ReserveDeadline
import com.hoppingmall.product.support.TestCacheConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.concurrent.atomic.AtomicReference

@DisplayName("내부 재고 예약 예산 헤더 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class InternalInventoryReserveBudgetTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var controller: InternalInventoryController

    @MockitoBean
    private lateinit var inventoryCommandService: InventoryCommandService

    @MockitoBean
    private lateinit var inventoryQueryService: InventoryQueryService

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    private val seenDeadline = AtomicReference<Long?>(NOT_CALLED)

    @AfterEach
    fun tearDown() {
        setBudgetEnabled(false)
        ReserveDeadline.clear()
    }

    @Test
    fun 플래그가_켜지고_헤더가_있으면_받은_시각에_예산을_더한_기한이_설정된다() {
        setBudgetEnabled(true)
        captureDeadlineOnBatchReserve()
        val before = System.currentTimeMillis()

        batchReserve(budgetHeader = "5000").andExpect { status { isOk() } }

        val after = System.currentTimeMillis()
        assertThat(seenDeadline.get()).isNotNull()
        assertThat(seenDeadline.get()!!).isBetween(before + 5000, after + 5000)
    }

    @Test
    fun 플래그가_꺼져_있으면_헤더가_와도_기한이_없다() {
        captureDeadlineOnBatchReserve()

        batchReserve(budgetHeader = "5000").andExpect { status { isOk() } }

        assertThat(seenDeadline.get()).isNull()
    }

    @Test
    fun 플래그가_켜져도_헤더가_없거나_0_이하이면_기한이_없다() {
        setBudgetEnabled(true)
        captureDeadlineOnBatchReserve()

        listOf(null, "0", "-5").forEach { header ->
            seenDeadline.set(NOT_CALLED)

            batchReserve(budgetHeader = header).andExpect { status { isOk() } }

            assertThat(seenDeadline.get()).describedAs("header=%s", header).isNull()
        }
    }

    @Test
    fun 요청이_예외로_끝나도_기한은_정리되어_다음_요청에_남지_않는다() {
        setBudgetEnabled(true)
        whenever(inventoryCommandService.batchReserveStock(any())).thenAnswer {
            seenDeadline.set(ReserveDeadline.current())
            throw InventoryBusyException()
        }

        batchReserve(budgetHeader = "5000").andExpect { status { isTooManyRequests() } }

        assertThat(seenDeadline.get()).isNotNull()
        assertThat(ReserveDeadline.current()).isNull()

        captureDeadlineOnBatchReserve()
        seenDeadline.set(NOT_CALLED)

        batchReserve(budgetHeader = null).andExpect { status { isOk() } }

        assertThat(seenDeadline.get()).isNull()
    }

    @Test
    fun 요청이_정상으로_끝나도_기한은_정리된다() {
        setBudgetEnabled(true)
        captureDeadlineOnBatchReserve()

        batchReserve(budgetHeader = "5000").andExpect { status { isOk() } }

        assertThat(seenDeadline.get()).isNotNull()
        assertThat(ReserveDeadline.current()).isNull()
    }

    @Test
    fun 단건_예약_엔드포인트는_예산_헤더를_읽지_않는다() {
        setBudgetEnabled(true)
        whenever(inventoryCommandService.reserveStock(1L, 2)).thenAnswer {
            seenDeadline.set(ReserveDeadline.current())
            "rsv-1"
        }

        mockMvc.post("/internal/api/v1/inventory/1/reserve") {
            param("quantity", "2")
            header("X-Internal-Token", internalToken)
            header(ReserveDeadline.HEADER, "5000")
        }.andExpect { status { isOk() } }

        assertThat(seenDeadline.get()).isNull()
    }

    @Test
    fun 예산_초과로_거절되면_429와_INV006을_응답한다() {
        setBudgetEnabled(true)
        whenever(inventoryCommandService.batchReserveStock(any())).thenThrow(InventoryBudgetExceededException())

        batchReserve(budgetHeader = "5000").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("INV006") }
        }
    }

    private fun batchReserve(budgetHeader: String?) =
        mockMvc.post("/internal/api/v1/inventory/batch-reserve") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"productId":1,"quantity":2}]"""
            header("X-Internal-Token", internalToken)
            if (budgetHeader != null) header(ReserveDeadline.HEADER, budgetHeader)
        }

    private fun captureDeadlineOnBatchReserve() {
        whenever(inventoryCommandService.batchReserveStock(any())).thenAnswer {
            seenDeadline.set(ReserveDeadline.current())
            mapOf(1L to "rsv-1")
        }
    }

    private fun setBudgetEnabled(enabled: Boolean) {
        ReflectionTestUtils.setField(controller, "budgetEnabled", enabled)
    }

    companion object {
        private const val NOT_CALLED = Long.MIN_VALUE
    }
}
