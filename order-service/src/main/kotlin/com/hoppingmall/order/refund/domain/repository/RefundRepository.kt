package com.hoppingmall.order.refund.domain.repository

import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.enum.RefundStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface RefundRepository : JpaRepository<Refund, Long> {
    fun findByOrderIdAndStatusNot(orderId: Long, status: RefundStatus): List<Refund>
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Page<Refund>
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<Refund>
    fun findBySellerIdAndStatusAndCompletedAtBetween(
        sellerId: Long, status: RefundStatus, startDate: LocalDateTime, endDate: LocalDateTime
    ): List<Refund>
}
