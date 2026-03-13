package com.hoppingmall.mall.wishlist.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.idempotency.Idempotent
import com.hoppingmall.mall.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.mall.wishlist.dto.response.WishlistResponse
import com.hoppingmall.mall.wishlist.service.WishlistCommandService
import com.hoppingmall.mall.wishlist.service.WishlistQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/wishlists")
@Tag(name = "위시리스트")
class WishlistController(
    private val wishlistCommandService: WishlistCommandService,
    private val wishlistQueryService: WishlistQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping
    fun addWishlist(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: WishlistCreateRequest
    ): ResponseEntity<ApiResponse<WishlistResponse>> {
        val wishlist = wishlistCommandService.addWishlist(userPrincipal.getUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(wishlist))
    }

    @Idempotent(ttlHours = 24)
    @DeleteMapping("/{wishlistId}")
    fun removeWishlist(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable wishlistId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        wishlistCommandService.removeWishlist(userPrincipal.getUserId(), wishlistId)
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @GetMapping
    fun getWishlists(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<WishlistResponse>>> {
        val wishlists = wishlistQueryService.getWishlists(userPrincipal.getUserId(), pageable)
        return ResponseEntity.ok(ApiResponse.success(wishlists))
    }

    @GetMapping("/check/{productId}")
    fun isWishlisted(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable productId: Long
    ): ResponseEntity<ApiResponse<Boolean>> {
        val wishlisted = wishlistQueryService.isWishlisted(userPrincipal.getUserId(), productId)
        return ResponseEntity.ok(ApiResponse.success(wishlisted))
    }
}
