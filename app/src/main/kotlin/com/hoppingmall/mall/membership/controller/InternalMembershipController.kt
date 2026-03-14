package com.hoppingmall.mall.membership.controller

import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.enum.MembershipGrade
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/memberships")
class InternalMembershipController(
    private val membershipRepository: MembershipRepository
) {

    @GetMapping("/by-user/{userId}/earning-rate")
    fun getPointEarningRate(@PathVariable userId: Long): ResponseEntity<BigDecimal> {
        val membership = membershipRepository.findByUserId(userId)
        val rate = membership?.grade?.pointEarningRate ?: MembershipGrade.BRONZE.pointEarningRate
        return ResponseEntity.ok(rate)
    }
}
