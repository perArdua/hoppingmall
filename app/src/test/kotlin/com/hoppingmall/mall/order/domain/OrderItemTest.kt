package com.hoppingmall.mall.order.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("OrderItem")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderItemTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 주문_항목을_생성한다() {
            val orderItem = OrderItem.create(
                orderId = 1L,
                sellerId = 5L,
                productId = 100L,
                productName = "테스트 상품",
                productPrice = BigDecimal("15000"),
                quantity = 3
            )

            assertEquals(1L, orderItem.orderId)
            assertEquals(5L, orderItem.sellerId)
            assertEquals(100L, orderItem.productId)
            assertEquals("테스트 상품", orderItem.productName)
            assertEquals(BigDecimal("15000"), orderItem.productPrice)
            assertEquals(3, orderItem.quantity)
            assertEquals(BigDecimal("45000"), orderItem.totalPrice)
        }

        @Test
        fun 총_가격이_자동_계산된다() {
            val orderItem = OrderItem.create(
                orderId = 1L,
                sellerId = 5L,
                productId = 100L,
                productName = "상품",
                productPrice = BigDecimal("25000"),
                quantity = 4
            )

            assertEquals(BigDecimal("100000"), orderItem.totalPrice)
        }
    }
}
