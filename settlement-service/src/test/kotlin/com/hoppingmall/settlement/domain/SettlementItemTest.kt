package com.hoppingmall.settlement.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("SettlementItem")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SettlementItemTest {

    @Test
    fun 정산_항목을_생성한다() {
        val item = SettlementItem.create(
            settlementId = 1L,
            orderId = 100L,
            orderItemId = 200L,
            productName = "테스트 상품",
            quantity = 3,
            salesAmount = BigDecimal("30000")
        )

        assertThat(item.settlementId).isEqualTo(1L)
        assertThat(item.orderId).isEqualTo(100L)
        assertThat(item.orderItemId).isEqualTo(200L)
        assertThat(item.productName).isEqualTo("테스트 상품")
        assertThat(item.quantity).isEqualTo(3)
        assertThat(item.salesAmount).isEqualByComparingTo(BigDecimal("30000"))
    }
}
