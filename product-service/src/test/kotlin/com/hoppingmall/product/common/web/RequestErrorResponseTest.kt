package com.hoppingmall.product.common.web

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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("잘못된 요청의 오류 응답 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class RequestErrorResponseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var inventoryCommandService: InventoryCommandService

    @MockitoBean
    private lateinit var inventoryQueryService: InventoryQueryService

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    @Test
    fun 없는_내부_경로로_POST하면_404를_응답한다() {
        mockMvc.post("/internal/api/v1/inventory/reservations/no-such-path/x") {
            header("X-Internal-Token", internalToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("REQ001") }
        }
    }

    @Test
    fun 로그인한_사용자가_없는_외부_경로를_GET하면_404를_응답한다() {
        mockMvc.get("/api/v1/no-such-path") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("REQ001") }
        }
    }

    @Test
    fun 예약_요청에_quantity_파라미터가_없으면_400을_응답하고_예약을_부르지_않는다() {
        mockMvc.post("/internal/api/v1/inventory/1/reserve") {
            header("X-Internal-Token", internalToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }

        verify(inventoryCommandService, never()).reserveStock(any(), any())
    }

    @Test
    fun 배치_예약_본문이_JSON으로_읽히지_않으면_400을_응답하고_예약을_부르지_않는다() {
        mockMvc.post("/internal/api/v1/inventory/batch-reserve") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"productId":1,"quantity":"""
            header("X-Internal-Token", internalToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }

        verify(inventoryCommandService, never()).batchReserveStock(any())
    }

    @Test
    fun 배치_취소_요청에_본문이_없으면_400을_응답하고_취소를_부르지_않는다() {
        mockMvc.post("/internal/api/v1/inventory/reservations/batch-cancel") {
            contentType = MediaType.APPLICATION_JSON
            header("X-Internal-Token", internalToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }

        verify(inventoryCommandService, never()).cancelReservations(any())
    }
}
