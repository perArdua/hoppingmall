package com.hoppingmall.mall.product.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("ProductStatistics")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductStatisticsTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 통계_생성_시_환불률이_올바르게_계산된다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 100,
                totalSalesAmount = BigDecimal("1000000"),
                totalRefundQuantity = 5,
                totalRefundAmount = BigDecimal("50000"),
                currentCartCount = 10,
                currentStock = 50
            )

            assertEquals(BigDecimal("0.0500"), stats.refundRate)
        }

        @Test
        fun 통계_생성_시_재고_회전율이_올바르게_계산된다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 100,
                totalSalesAmount = BigDecimal("1000000"),
                totalRefundQuantity = 5,
                totalRefundAmount = BigDecimal("50000"),
                currentCartCount = 10,
                currentStock = 50
            )

            assertEquals(BigDecimal("2.0000"), stats.stockTurnoverRate)
        }

        @Test
        fun 판매수량이_0이면_환불률은_0이다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 0,
                totalSalesAmount = BigDecimal.ZERO,
                totalRefundQuantity = 0,
                totalRefundAmount = BigDecimal.ZERO,
                currentCartCount = 0,
                currentStock = 50
            )

            assertEquals(BigDecimal.ZERO, stats.refundRate)
        }

        @Test
        fun 재고가_0이면_재고_회전율은_0이다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 100,
                totalSalesAmount = BigDecimal("1000000"),
                totalRefundQuantity = 5,
                totalRefundAmount = BigDecimal("50000"),
                currentCartCount = 10,
                currentStock = 0
            )

            assertEquals(BigDecimal.ZERO, stats.stockTurnoverRate)
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {
        @Test
        fun 통계_갱신_시_환불률과_재고_회전율이_재계산된다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 100,
                totalSalesAmount = BigDecimal("1000000"),
                totalRefundQuantity = 5,
                totalRefundAmount = BigDecimal("50000"),
                currentCartCount = 10,
                currentStock = 50
            )

            stats.update(
                productName = "수정된 상품",
                categoryId = 2L,
                totalSalesQuantity = 200,
                totalSalesAmount = BigDecimal("2000000"),
                totalRefundQuantity = 20,
                totalRefundAmount = BigDecimal("200000"),
                currentCartCount = 15,
                currentStock = 100
            )

            assertEquals("수정된 상품", stats.productName)
            assertEquals(2L, stats.categoryId)
            assertEquals(200, stats.totalSalesQuantity)
            assertEquals(BigDecimal("0.1000"), stats.refundRate)
            assertEquals(BigDecimal("2.0000"), stats.stockTurnoverRate)
        }
    }
}
