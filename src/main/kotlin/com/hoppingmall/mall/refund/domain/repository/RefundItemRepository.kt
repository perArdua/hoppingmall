package com.hoppingmall.mall.refund.domain.repository

import com.hoppingmall.mall.refund.domain.RefundItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RefundItemRepository : JpaRepository<RefundItem, Long> {
    fun findByRefundId(refundId: Long): List<RefundItem>
}
