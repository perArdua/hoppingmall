package com.hoppingmall.payment.payment.domain.repository

import com.hoppingmall.payment.payment.domain.PaymentEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentEventLogRepository : JpaRepository<PaymentEventLog, Long> {
    fun existsByTransactionId(transactionId: String): Boolean
}
