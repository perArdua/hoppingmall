package com.hoppingmall.mall.payment.domain.repository

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.enum.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByOrderId(orderId: Long): Payment?

    fun findByUserId(userId: Long): List<Payment>
    
    fun findByUserId(userId: Long, pageable: Pageable): Page<Payment>

    fun findByTransactionId(transactionId: String): Payment?
}