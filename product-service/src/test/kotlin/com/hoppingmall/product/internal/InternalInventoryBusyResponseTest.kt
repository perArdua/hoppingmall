package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.exception.InventoryBusyException
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
import com.hoppingmall.product.support.TestCacheConfig
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@DisplayName("내부 재고 예약 혼잡 응답 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class InternalInventoryBusyResponseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var inventoryCommandService: InventoryCommandService

    @MockitoBean
    private lateinit var inventoryQueryService: InventoryQueryService

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    @Test
    fun 단건_예약이_혼잡으로_거절되면_429와_INV005를_응답한다() {
        whenever(inventoryCommandService.reserveStock(1L, 2)).thenThrow(InventoryBusyException())

        mockMvc.post("/internal/api/v1/inventory/1/reserve") {
            param("quantity", "2")
            header("X-Internal-Token", internalToken)
        }.andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("INV005") }
        }
    }

    @Test
    fun 배치_예약이_혼잡으로_거절되면_429와_INV005를_응답한다() {
        whenever(inventoryCommandService.batchReserveStock(any())).thenThrow(InventoryBusyException())

        mockMvc.post("/internal/api/v1/inventory/batch-reserve") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"productId":1,"quantity":2}]"""
            header("X-Internal-Token", internalToken)
        }.andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("INV005") }
        }
    }
}
