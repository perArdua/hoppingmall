package com.hoppingmall.mall.order.domain.repository

import com.hoppingmall.mall.order.domain.Order
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Slice<Order>
}
