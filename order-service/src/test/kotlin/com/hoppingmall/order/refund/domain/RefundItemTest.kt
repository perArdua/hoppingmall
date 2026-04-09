package com.hoppingmall.order.refund.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("RefundItem")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundItemTest {

    @Test
    fun 환불_항목을_생성한다() {
        val item = RefundItem.create(
            refundId = 1L,
            orderItemId = 10L,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("10000"),
            quantity = 3
        )

        assertThat(item.refundId).isEqualTo(1L)
        assertThat(item.orderItemId).isEqualTo(10L)
        assertThat(item.productId).isEqualTo(100L)
        assertThat(item.productName).isEqualTo("테스트 상품")
        assertThat(item.productPrice).isEqualByComparingTo(BigDecimal("10000"))
        assertThat(item.quantity).isEqualTo(3)
        assertThat(item.refundPrice).isEqualByComparingTo(BigDecimal("30000"))
    }

    @Test
    fun 수량이_1일_때_환불금액은_단가와_같다() {
        val item = RefundItem.create(
            refundId = 1L,
            orderItemId = 10L,
            productId = 100L,
            productName = "상품A",
            productPrice = BigDecimal("25000"),
            quantity = 1
        )

        assertThat(item.refundPrice).isEqualByComparingTo(BigDecimal("25000"))
    }
}
