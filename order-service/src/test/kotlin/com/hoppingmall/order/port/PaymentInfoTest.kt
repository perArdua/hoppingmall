package com.hoppingmall.order.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("PaymentInfo")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentInfoTest {

    @Test
    fun SUCCESS_상태이면_isSuccess가_true이다() {
        val paymentInfo = PaymentInfo(
            id = 1L, orderId = 10L, amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"), couponId = null, status = "SUCCESS"
        )

        assertThat(paymentInfo.isSuccess()).isTrue()
    }

    @Test
    fun SUCCESS가_아니면_isSuccess가_false이다() {
        val paymentInfo = PaymentInfo(
            id = 1L, orderId = 10L, amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"), couponId = null, status = "FAILED"
        )

        assertThat(paymentInfo.isSuccess()).isFalse()
    }

    @Test
    fun PENDING_상태이면_isSuccess가_false이다() {
        val paymentInfo = PaymentInfo(
            id = 1L, orderId = 10L, amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"), couponId = 1L, status = "PENDING"
        )

        assertThat(paymentInfo.isSuccess()).isFalse()
    }
}
