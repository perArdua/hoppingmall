package com.hoppingmall.mall.settlement.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.mall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.mall.settlement.dto.response.SettlementResponse
import com.hoppingmall.mall.settlement.enum.SettlementStatus
import com.hoppingmall.mall.settlement.service.SettlementCommandService
import com.hoppingmall.mall.settlement.service.SettlementQueryService
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.exception.seller.SellerNotFoundException
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "정산")
class SettlementController(
    private val settlementCommandService: SettlementCommandService,
    private val settlementQueryService: SettlementQueryService,
    private val sellerRepository: SellerRepository
) {

    @PostMapping("/api/v1/admin/settlements")
    fun createSettlement(
        @RequestBody request: CreateSettlementRequest
    ): ResponseEntity<ApiResponse<SettlementResponse>> {
        val result = settlementCommandService.createSettlement(request)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @PatchMapping("/api/v1/admin/settlements/{settlementId}/confirm")
    fun confirmSettlement(
        @PathVariable settlementId: Long
    ): ResponseEntity<ApiResponse<SettlementResponse>> {
        val result = settlementCommandService.confirmSettlement(settlementId)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @PatchMapping("/api/v1/admin/settlements/{settlementId}/pay")
    fun paySettlement(
        @PathVariable settlementId: Long
    ): ResponseEntity<ApiResponse<SettlementResponse>> {
        val result = settlementCommandService.paySettlement(settlementId)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/api/v1/admin/settlements")
    fun getSettlementsForAdmin(
        @RequestParam(required = false) sellerId: Long?,
        @RequestParam(required = false) status: SettlementStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<SettlementResponse>>> {
        val result = settlementQueryService.getSettlements(sellerId, status, pageable)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/api/v1/admin/settlements/{settlementId}")
    fun getSettlementDetailForAdmin(
        @PathVariable settlementId: Long
    ): ResponseEntity<ApiResponse<SettlementDetailResponse>> {
        val result = settlementQueryService.getSettlementDetail(settlementId, null)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/api/v1/seller/settlements")
    fun getSettlementsForSeller(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @RequestParam(required = false) status: SettlementStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<SettlementResponse>>> {
        val seller = sellerRepository.findNullableByUserId(userPrincipal.getUserId())
            ?: throw SellerNotFoundException()
        val result = settlementQueryService.getSettlements(seller.id, status, pageable)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/api/v1/seller/settlements/{settlementId}")
    fun getSettlementDetailForSeller(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable settlementId: Long
    ): ResponseEntity<ApiResponse<SettlementDetailResponse>> {
        val seller = sellerRepository.findNullableByUserId(userPrincipal.getUserId())
            ?: throw SellerNotFoundException()
        val result = settlementQueryService.getSettlementDetail(settlementId, seller.id)
        return ResponseEntity.ok(ApiResponse.success(result))
    }
}
