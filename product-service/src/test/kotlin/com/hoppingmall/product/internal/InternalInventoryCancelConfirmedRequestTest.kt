package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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

@DisplayName("내부 확정된 예약 취소 요청 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class InternalInventoryCancelConfirmedRequestTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var inventoryCommandService: InventoryCommandService

    @MockitoBean
    private lateinit var inventoryQueryService: InventoryQueryService

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    @Test
    fun 배치_취소와_같은_모양의_요청을_batch_cancel_confirmed로_받으면_확정된_예약_취소만_부르고_200을_응답한다() {
        mockMvc.post("/internal/api/v1/inventory/reservations/batch-cancel-confirmed") {
            contentType = MediaType.APPLICATION_JSON
            content = """["rsv-1","rsv-2"]"""
            header("X-Internal-Token", internalToken)
        }.andExpect {
            status { isOk() }
            content { string("") }
        }

        verify(inventoryCommandService).cancelConfirmedReservations(listOf("rsv-1", "rsv-2"))
        verify(inventoryCommandService, never()).cancelReservations(any())
    }
}
