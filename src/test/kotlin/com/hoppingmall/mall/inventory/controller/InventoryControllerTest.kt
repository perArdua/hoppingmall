package com.hoppingmall.mall.inventory.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.mall.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.mall.inventory.dto.response.InventoryResponse
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.inventory.service.InventoryQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity

@DisplayName("InventoryController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InventoryControllerTest {

    private val inventoryCommandService: InventoryCommandService = mock()
    private val inventoryQueryService: InventoryQueryService = mock()
    private val controller = InventoryController(inventoryCommandService, inventoryQueryService)

    @Nested
    @DisplayName("initStock")
    inner class InitStock {
        @Test
        fun 재고_초기화_성공() {
            // Data
            val request = InventoryInitRequest(productId = 1L, stockQuantity = 100)
            val expectedResponse = InventoryResponse(id = 1L, productId = 1L, stockQuantity = 100)

            // Context
            whenever(inventoryCommandService.initStock(request)).thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<InventoryResponse>> = controller.initStock(request)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponse, response.body?.data)
            verify(inventoryCommandService).initStock(request)
        }
    }

    @Nested
    @DisplayName("getStock")
    inner class GetStock {
        @Test
        fun 재고_조회_성공() {
            // Data
            val productId = 1L
            val expectedResponse = InventoryResponse(id = 1L, productId = productId, stockQuantity = 100)

            // Context
            whenever(inventoryQueryService.getStock(productId)).thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<InventoryResponse>> = controller.getStock(productId)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponse, response.body?.data)
            verify(inventoryQueryService).getStock(productId)
        }
    }

    @Nested
    @DisplayName("updateStock")
    inner class UpdateStock {
        @Test
        fun 재고_수량_변경_성공() {
            // Data
            val productId = 1L
            val request = InventoryUpdateRequest(stockQuantity = 200)
            val expectedResponse = InventoryResponse(id = 1L, productId = productId, stockQuantity = 200)

            // Context
            whenever(inventoryCommandService.updateStock(productId, request)).thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<InventoryResponse>> = controller.updateStock(productId, request)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponse, response.body?.data)
            verify(inventoryCommandService).updateStock(productId, request)
        }
    }
}
