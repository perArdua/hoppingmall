package com.hoppingmall.order.order.domain.repository

import com.hoppingmall.order.order.domain.Order
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Slice<Order>

    @Query(value = "SELECT id FROM orders WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun lockById(@Param("id") id: Long): Long?
}
