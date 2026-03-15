package com.hoppingmall.user.internal

import com.hoppingmall.user.domain.repository.SellerRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/api/v1")
class InternalUserController(
    private val sellerRepository: SellerRepository
) {

    @GetMapping("/sellers/by-user/{userId}")
    fun getSellerByUserId(@PathVariable userId: Long): ResponseEntity<SellerResponse> {
        val seller = sellerRepository.findNullableByUserId(userId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            SellerResponse(
                id = seller.id!!,
                userId = seller.userId,
                businessNumber = seller.businessNumber,
                approvalStatus = seller.getApprovalStatus().name
            )
        )
    }

    data class SellerResponse(
        val id: Long,
        val userId: Long,
        val businessNumber: String,
        val approvalStatus: String
    )
}
