package com.hoppingmall.user.internal

import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.domain.repository.MembershipRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1")
class InternalMembershipController(
    private val membershipRepository: MembershipRepository
) {

    @GetMapping("/memberships/by-user/{userId}/earning-rate")
    fun getEarningRate(@PathVariable userId: Long): ResponseEntity<BigDecimal> {
        val membership = membershipRepository.findByUserId(userId)
        val rate = membership?.grade?.pointEarningRate ?: MembershipGrade.BRONZE.pointEarningRate
        return ResponseEntity.ok(rate)
    }
}
