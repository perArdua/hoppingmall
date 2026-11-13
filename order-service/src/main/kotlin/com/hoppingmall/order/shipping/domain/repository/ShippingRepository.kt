package com.hoppingmall.order.shipping.domain.repository

import com.hoppingmall.order.shipping.domain.Shipping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ShippingRepository : JpaRepository<Shipping, Long> {
    fun findByOrderId(orderId: Long): Shipping?
    fun findByTrackingNumber(trackingNumber: String): Shipping?

    @Query(value = "SELECT order_id FROM shippings WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun lockOrderIdById(@Param("id") id: Long): Long?
}
