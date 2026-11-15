package com.hoppingmall.order.refund.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("RefundPointShare")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class RefundPointShareTest {

    @Test
    fun 부분_환불은_정가_비율로_내림한다() {
        val share = RefundPointShare.of(
            usedPoints = BigDecimal("1000"),
            orderTotal = BigDecimal("30000"),
            refundedGross = BigDecimal("10000"),
            alreadyRefundedPoints = BigDecimal.ZERO,
            isFullRefund = false
        )

        assertThat(share).isEqualByComparingTo("333")
    }

    @Test
    fun 마지막_환불은_앞_환불을_뺀_나머지를_돌려준다() {
        val share = RefundPointShare.of(
            usedPoints = BigDecimal("1000"),
            orderTotal = BigDecimal("30000"),
            refundedGross = BigDecimal("10000"),
            alreadyRefundedPoints = BigDecimal("666"),
            isFullRefund = true
        )

        assertThat(share).isEqualByComparingTo("334")
    }

    @Test
    fun 부분_환불_몫은_남은_포인트를_넘지_않는다() {
        val share = RefundPointShare.of(
            usedPoints = BigDecimal("1000"),
            orderTotal = BigDecimal("30000"),
            refundedGross = BigDecimal("20000"),
            alreadyRefundedPoints = BigDecimal("900"),
            isFullRefund = false
        )

        assertThat(share).isEqualByComparingTo("100")
    }

    @Test
    fun 앞_환불이_쓴_포인트보다_많이_돌려줬어도_음수가_되지_않는다() {
        val share = RefundPointShare.of(
            usedPoints = BigDecimal("1000"),
            orderTotal = BigDecimal("30000"),
            refundedGross = BigDecimal("10000"),
            alreadyRefundedPoints = BigDecimal("1200"),
            isFullRefund = true
        )

        assertThat(share).isEqualByComparingTo("0")
    }

    @Test
    fun 포인트를_쓰지_않았거나_주문_금액이_0이면_0이다() {
        assertThat(RefundPointShare.of(BigDecimal.ZERO, BigDecimal("30000"), BigDecimal("10000"), BigDecimal.ZERO, false))
            .isEqualByComparingTo("0")
        assertThat(RefundPointShare.of(BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false))
            .isEqualByComparingTo("0")
    }
}
