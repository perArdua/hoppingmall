package com.hoppingmall.mall.cartItem.controller

import com.hoppingmall.mall.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.mall.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import com.hoppingmall.mall.cartItem.service.CartItemCommandService
import com.hoppingmall.mall.cartItem.service.CartItemQueryService
import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/cart-items")
class CartItemController(
    private val cartItemCommandService: CartItemCommandService,
    private val cartItemQueryService: CartItemQueryService
) {

    @PostMapping
    fun addCartItem(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: CartItemCreateRequest
    ): ResponseEntity<ApiResponse<CartItemResponse>> {
        val cartItem = cartItemCommandService.addCartItem(userPrincipal.getUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(cartItem))
    }

    @GetMapping
    fun getCartItems(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<ApiResponse<List<CartItemResponse>>> {
        val cartItems = cartItemQueryService.getCartItems(userPrincipal.getUserId())
        return ResponseEntity.ok(ApiResponse.success(cartItems))
    }

    @PatchMapping("/{cartItemId}")
    fun updateCartItemQuantity(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable cartItemId: Long,
        @Valid @RequestBody request: CartItemUpdateRequest
    ): ResponseEntity<ApiResponse<CartItemResponse>> {
        val cartItem = cartItemCommandService.updateCartItemQuantity(userPrincipal.getUserId(), cartItemId, request)
        return ResponseEntity.ok(ApiResponse.success(cartItem))
    }

    @DeleteMapping("/{cartItemId}")
    fun removeCartItem(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable cartItemId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        cartItemCommandService.removeCartItem(userPrincipal.getUserId(), cartItemId)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
} 