package com.hoppingmall.mall.order.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.idempotency.Idempotent
import com.hoppingmall.mall.order.dto.request.OrderCreateRequest
import com.hoppingmall.mall.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.mall.order.dto.response.OrderResponse
import com.hoppingmall.mall.order.service.OrderCommandService
import com.hoppingmall.mall.order.service.OrderQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderCommandService: OrderCommandService,
    private val orderQueryService: OrderQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping
    fun createOrder(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: OrderCreateRequest
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderCommandService.createOrder(userPrincipal.getUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(order))
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable orderId: Long
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderQueryService.getOrder(orderId, userPrincipal.getUserId())
        return ResponseEntity.ok(ApiResponse.success(order))
    }

    @GetMapping
    fun getMyOrders(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<ApiResponse<Slice<OrderResponse>>> {
        val orders = orderQueryService.getMyOrders(userPrincipal.getUserId(), pageable)
        return ResponseEntity.ok(ApiResponse.success(orders))
    }

    @PatchMapping("/{orderId}/cancel")
    fun cancelOrder(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable orderId: Long
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderCommandService.cancelOrder(userPrincipal.getUserId(), orderId)
        return ResponseEntity.ok(ApiResponse.success(order))
    }

    @PatchMapping("/{orderId}/status")
    fun updateOrderStatus(
        @PathVariable orderId: Long,
        @Valid @RequestBody request: OrderStatusUpdateRequest
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderCommandService.updateOrderStatus(orderId, request)
        return ResponseEntity.ok(ApiResponse.success(order))
    }
}
