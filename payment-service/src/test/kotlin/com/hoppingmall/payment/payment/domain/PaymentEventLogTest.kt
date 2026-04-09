package com.hoppingmall.payment.payment.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("PaymentEventLog 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PaymentEventLogTest {

    @Test
    fun 생성자가_transactionId와_paymentId와_orderId를_올바르게_설정한다() {
        val log = PaymentEventLog(
            transactionId = "txn-001",
            paymentId = 42L,
            orderId = 99L
        )

        assertThat(log.transactionId).isEqualTo("txn-001")
        assertThat(log.paymentId).isEqualTo(42L)
        assertThat(log.orderId).isEqualTo(99L)
    }
}
