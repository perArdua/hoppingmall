package com.hoppingmall.order.cartItem.controller

import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.cartItem.service.CartItemCommandService
import com.hoppingmall.order.cartItem.service.CartItemQueryService
import com.hoppingmall.order.common.ApiResponse
import com.hoppingmall.order.common.UserPrincipal
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/cart-items")
@Tag(name = "장바구니")
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
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<ApiResponse<Slice<CartItemResponse>>> {
        val cartItems = cartItemQueryService.getCartItems(userPrincipal.getUserId(), pageable)
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
