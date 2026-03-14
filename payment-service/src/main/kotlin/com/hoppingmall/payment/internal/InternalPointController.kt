package com.hoppingmall.payment.internal

import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/points")
class InternalPointController(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository
) {

    @GetMapping("/by-user/{userId}/balance")
    fun getPointBalance(@PathVariable userId: Long): ResponseEntity<PointBalanceInfo> {
        val point = pointRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(PointBalanceInfo(userId = userId, balance = point.balance))
    }

    data class PointBalanceInfo(
        val userId: Long,
        val balance: BigDecimal
    )
}
