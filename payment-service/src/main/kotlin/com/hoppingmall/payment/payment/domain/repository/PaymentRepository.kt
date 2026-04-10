package com.hoppingmall.payment.payment.domain.repository

import com.hoppingmall.payment.payment.domain.Payment
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByOrderId(orderId: Long): Payment?

    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdForUpdate(@Param("id") id: Long): Payment?

    fun findByUserId(userId: Long, pageable: Pageable): Slice<Payment>

    fun findByTransactionId(transactionId: String): Payment?
}
