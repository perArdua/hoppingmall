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

        @Test
        fun 생성_시_순매출이_올바르게_계산된다() {
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

            assertEquals(BigDecimal("950000"), stats.netRevenue)
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
            assertEquals(BigDecimal("1800000"), stats.netRevenue)
        }
    }

    @Nested
    @DisplayName("incrementSales")
    inner class IncrementSales {
        @Test
        fun 판매_증가_시_수량_금액_주문수가_증가한다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L
            )

            stats.incrementSales(3, BigDecimal("30000"))

            assertEquals(3, stats.totalSalesQuantity)
            assertEquals(BigDecimal("30000"), stats.totalSalesAmount)
            assertEquals(3, stats.todaySalesQuantity)
            assertEquals(BigDecimal("30000"), stats.todaySalesAmount)
            assertEquals(1, stats.todayOrderCount)
            assertEquals(1, stats.orderCount)
            assertEquals(BigDecimal("30000"), stats.netRevenue)
            assertEquals(BigDecimal("30000.00"), stats.averageOrderAmount)
        }

        @Test
        fun 연속_판매_증가_시_누적된다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L
            )

            stats.incrementSales(2, BigDecimal("20000"))
            stats.incrementSales(3, BigDecimal("45000"))

            assertEquals(5, stats.totalSalesQuantity)
            assertEquals(BigDecimal("65000"), stats.totalSalesAmount)
            assertEquals(2, stats.orderCount)
            assertEquals(BigDecimal("32500.00"), stats.averageOrderAmount)
        }
    }

    @Nested
    @DisplayName("decrementSales")
    inner class DecrementSales {
        @Test
        fun 판매_차감_시_수량과_금액이_감소한다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 10,
                totalSalesAmount = BigDecimal("100000")
            )

            stats.decrementSales(3, BigDecimal("30000"))

            assertEquals(7, stats.totalSalesQuantity)
            assertEquals(BigDecimal("70000"), stats.totalSalesAmount)
        }

        @Test
        fun 판매_차감_시_0_미만이_되지_않는다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 2,
                totalSalesAmount = BigDecimal("20000")
            )

            stats.decrementSales(5, BigDecimal("50000"))

            assertEquals(0, stats.totalSalesQuantity)
            assertEquals(BigDecimal.ZERO, stats.totalSalesAmount)
        }
    }

    @Nested
    @DisplayName("incrementRefund")
    inner class IncrementRefund {
        @Test
        fun 환불_증가_시_환불_수량_금액이_증가한다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 100,
                totalSalesAmount = BigDecimal("1000000")
            )

            stats.incrementRefund(2, BigDecimal("20000"))

            assertEquals(2, stats.totalRefundQuantity)
            assertEquals(BigDecimal("20000"), stats.totalRefundAmount)
            assertEquals(2, stats.todayRefundQuantity)
            assertEquals(BigDecimal("20000"), stats.todayRefundAmount)
            assertEquals(BigDecimal("980000"), stats.netRevenue)
            assertEquals(BigDecimal("0.0200"), stats.refundRate)
        }
    }

    @Nested
    @DisplayName("resetToday")
    inner class ResetToday {
        @Test
        fun 오늘_지표가_모두_초기화된다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L
            )
            stats.incrementSales(5, BigDecimal("50000"))
            stats.incrementRefund(1, BigDecimal("10000"))

            stats.resetToday()

            assertEquals(0, stats.todaySalesQuantity)
            assertEquals(BigDecimal.ZERO, stats.todaySalesAmount)
            assertEquals(0, stats.todayOrderCount)
            assertEquals(0, stats.todayRefundQuantity)
            assertEquals(BigDecimal.ZERO, stats.todayRefundAmount)
            assertEquals(5, stats.totalSalesQuantity)
            assertEquals(1, stats.totalRefundQuantity)
        }
    }

    @Nested
    @DisplayName("updatePeriodMetrics")
    inner class UpdatePeriodMetrics {
        @Test
        fun 기간_지표와_성장률이_올바르게_계산된다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L
            )

            stats.updatePeriodMetrics(
                last7Days = BigDecimal("700000"),
                last30Days = BigDecimal("3000000"),
                previousWeekAmount = BigDecimal("500000")
            )

            assertEquals(BigDecimal("700000"), stats.last7DaysSalesAmount)
            assertEquals(BigDecimal("3000000"), stats.last30DaysSalesAmount)
            assertEquals(BigDecimal("40.0000"), stats.salesGrowthRate)
        }

        @Test
        fun 전주_매출이_0이면_성장률은_0이다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L
            )

            stats.updatePeriodMetrics(
                last7Days = BigDecimal("700000"),
                last30Days = BigDecimal("3000000"),
                previousWeekAmount = BigDecimal.ZERO
            )

            assertEquals(BigDecimal.ZERO, stats.salesGrowthRate)
        }
    }

    @Nested
    @DisplayName("updateCartAndInventory")
    inner class UpdateCartAndInventory {
        @Test
        fun 장바구니와_재고가_갱신된다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 100
            )

            stats.updateCartAndInventory(cartCount = 15, stock = 25)

            assertEquals(15, stats.currentCartCount)
            assertEquals(25, stats.currentStock)
            assertEquals(BigDecimal("4.0000"), stats.stockTurnoverRate)
        }
    }
}
