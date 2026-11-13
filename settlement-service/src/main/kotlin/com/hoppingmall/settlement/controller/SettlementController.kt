package com.hoppingmall.settlement.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementDetailResponse
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.enums.SettlementStatus
import com.hoppingmall.settlement.exception.SettlementInvalidSellerException
import com.hoppingmall.settlement.exception.SettlementSellerNotFoundException
import com.hoppingmall.settlement.port.SellerQueryPort
import com.hoppingmall.settlement.service.SettlementCommandService
import com.hoppingmall.settlement.service.SettlementQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@Tag(name = "정산")
@RestController
class SettlementController(
    private val settlementCommandService: SettlementCommandService,
    private val settlementQueryService: SettlementQueryService,
    private val sellerQueryPort: SellerQueryPort
) {

    @Operation(description = "정산을 만든다. 요청의 seller_id는 승인된 판매자의 사용자 id(users.id)다. 판매자 행 id(sellers.id)가 아니다. 승인된 판매자의 사용자 id가 아니면 400(STL010).")
    @PostMapping("/api/v1/admin/settlements")
    fun createSettlement(
        @Valid @RequestBody request: CreateSettlementRequest
    ): ResponseEntity<ApiResponse<SettlementResponse>> {
        val seller = sellerQueryPort.findByUserId(request.sellerId)
        if (seller == null || !seller.approved) {
            throw SettlementInvalidSellerException()
        }
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
        @Parameter(description = "판매자의 사용자 id(users.id). 판매자 행 id(sellers.id)가 아니다.")
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
        val userId = userPrincipal.getUserId()
        sellerQueryPort.findByUserId(userId) ?: throw SettlementSellerNotFoundException()
        val result = settlementQueryService.getSettlements(userId, status, pageable)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/api/v1/seller/settlements/{settlementId}")
    fun getSettlementDetailForSeller(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable settlementId: Long
    ): ResponseEntity<ApiResponse<SettlementDetailResponse>> {
        val userId = userPrincipal.getUserId()
        sellerQueryPort.findByUserId(userId) ?: throw SettlementSellerNotFoundException()
        val result = settlementQueryService.getSettlementDetail(settlementId, userId)
        return ResponseEntity.ok(ApiResponse.success(result))
    }
}
