package com.hoppingmall.payment.payment.domain.repository

import com.hoppingmall.payment.payment.domain.Payment
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByOrderId(orderId: Long): Payment?

    fun findByUserId(userId: Long): List<Payment>

    fun findByUserId(userId: Long, pageable: Pageable): Slice<Payment>

    fun findByTransactionId(transactionId: String): Payment?
}
