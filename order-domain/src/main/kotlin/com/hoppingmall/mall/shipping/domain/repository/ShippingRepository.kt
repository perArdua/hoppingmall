package com.hoppingmall.mall.shipping.domain.repository

import com.hoppingmall.mall.shipping.domain.Shipping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ShippingRepository : JpaRepository<Shipping, Long> {
    fun findByOrderId(orderId: Long): Shipping?
    fun findByTrackingNumber(trackingNumber: String): Shipping?
}
