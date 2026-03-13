package com.hoppingmall.mall.shipping.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.idempotency.Idempotent
import com.hoppingmall.mall.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.mall.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.mall.shipping.dto.response.ShippingResponse
import com.hoppingmall.mall.shipping.service.ShippingCommandService
import com.hoppingmall.mall.shipping.service.ShippingQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/shipping")
@Tag(name = "배송")
class ShippingController(
    private val shippingCommandService: ShippingCommandService,
    private val shippingQueryService: ShippingQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping
    fun createShipping(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: ShippingCreateRequest
    ): ResponseEntity<ApiResponse<ShippingResponse>> {
        val shipping = shippingCommandService.createShipping(userPrincipal.getUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(shipping))
    }

    @Idempotent(ttlHours = 24)
    @PatchMapping("/{shippingId}/status")
    fun updateShippingStatus(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable shippingId: Long,
        @Valid @RequestBody request: ShippingStatusUpdateRequest
    ): ResponseEntity<ApiResponse<ShippingResponse>> {
        val shipping = shippingCommandService.updateShippingStatus(userPrincipal.getUserId(), shippingId, request)
        return ResponseEntity.ok(ApiResponse.success(shipping))
    }

    @GetMapping("/order/{orderId}")
    fun getShippingByOrderId(
        @PathVariable orderId: Long
    ): ResponseEntity<ApiResponse<ShippingResponse>> {
        val shipping = shippingQueryService.getShippingByOrderId(orderId)
        return ResponseEntity.ok(ApiResponse.success(shipping))
    }

    @GetMapping("/{shippingId}")
    fun getShipping(
        @PathVariable shippingId: Long
    ): ResponseEntity<ApiResponse<ShippingResponse>> {
        val shipping = shippingQueryService.getShipping(shippingId)
        return ResponseEntity.ok(ApiResponse.success(shipping))
    }
}
