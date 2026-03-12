package com.hoppingmall.mall.inventory.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.idempotency.Idempotent
import com.hoppingmall.mall.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.mall.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.mall.inventory.dto.response.InventoryResponse
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.inventory.service.InventoryQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/inventories")
class InventoryController(
    private val inventoryCommandService: InventoryCommandService,
    private val inventoryQueryService: InventoryQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping
    fun initStock(
        @Valid @RequestBody request: InventoryInitRequest
    ): ResponseEntity<ApiResponse<InventoryResponse>> {
        val response = inventoryCommandService.initStock(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    @GetMapping("/{productId}")
    fun getStock(
        @PathVariable productId: Long
    ): ResponseEntity<ApiResponse<InventoryResponse>> {
        val response = inventoryQueryService.getStock(productId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @Idempotent(ttlHours = 24)
    @PatchMapping("/{productId}")
    fun updateStock(
        @PathVariable productId: Long,
        @Valid @RequestBody request: InventoryUpdateRequest
    ): ResponseEntity<ApiResponse<InventoryResponse>> {
        val response = inventoryCommandService.updateStock(productId, request)
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
