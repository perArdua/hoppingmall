package com.hoppingmall.product.inventory.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.Idempotent
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.dto.response.InventoryResponse
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
import com.hoppingmall.product.product.service.ProductOwnershipPolicy
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/inventories")
@Tag(name = "재고")
class InventoryController(
    private val inventoryCommandService: InventoryCommandService,
    private val inventoryQueryService: InventoryQueryService,
    private val productOwnershipPolicy: ProductOwnershipPolicy
) {

    @Idempotent(ttlHours = 24)
    @PostMapping
    fun initStock(
        @Valid @RequestBody request: InventoryInitRequest,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<ApiResponse<InventoryResponse>> {
        productOwnershipPolicy.checkOwner(request.productId, userPrincipal)
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
        @Valid @RequestBody request: InventoryUpdateRequest,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<ApiResponse<InventoryResponse>> {
        productOwnershipPolicy.checkOwner(productId, userPrincipal)
        val response = inventoryCommandService.updateStock(productId, request)
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
