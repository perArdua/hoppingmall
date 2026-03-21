package com.hoppingmall.settlement.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.enums.SettlementStatus
import com.hoppingmall.settlement.exception.SettlementSellerNotFoundException
import com.hoppingmall.settlement.port.SellerQueryPort
import com.hoppingmall.settlement.service.SettlementCommandService
import com.hoppingmall.settlement.service.SettlementQueryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
class SettlementController(
    private val settlementCommandService: SettlementCommandService,
    private val settlementQueryService: SettlementQueryService,
    private val sellerQueryPort: SellerQueryPort
) {

    @PostMapping("/api/v1/admin/settlements")
    fun createSettlement(
        @Valid @RequestBody request: CreateSettlementRequest
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
        val seller = sellerQueryPort.findByUserId(userPrincipal.getUserId())
            ?: throw SettlementSellerNotFoundException()
        val result = settlementQueryService.getSettlements(seller.id, status, pageable)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/api/v1/seller/settlements/{settlementId}")
    fun getSettlementDetailForSeller(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable settlementId: Long
    ): ResponseEntity<ApiResponse<SettlementDetailResponse>> {
        val seller = sellerQueryPort.findByUserId(userPrincipal.getUserId())
            ?: throw SettlementSellerNotFoundException()
        val result = settlementQueryService.getSettlementDetail(settlementId, seller.id)
        return ResponseEntity.ok(ApiResponse.success(result))
    }
}
