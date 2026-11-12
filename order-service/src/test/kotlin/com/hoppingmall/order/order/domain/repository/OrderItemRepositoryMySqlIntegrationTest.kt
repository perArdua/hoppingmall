package com.hoppingmall.order.order.domain.repository

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("주문 항목 저장소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderItemRepositoryMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Test
    fun 상품별_판매_집계는_지정한_상태의_주문만_더하고_삭제된_주문과_항목은_뺀다() {
        val paid = order("PAID")
        val delivered = order("DELIVERED")
        val cancelled = order("CANCELLED")
        val deletedOrder = order("PAID", deleted = true)
        item(paid, productId = 100L, price = "1000", quantity = 2)
        item(delivered, productId = 100L, price = "1000", quantity = 3)
        item(delivered, productId = 200L, price = "2500", quantity = 1)
        item(cancelled, productId = 100L, price = "1000", quantity = 7)
        item(deletedOrder, productId = 200L, price = "2500", quantity = 9)
        item(paid, productId = 200L, price = "2500", quantity = 11, deleted = true)

        val result = orderItemRepository.aggregateSalesByProduct(listOf("PAID", "DELIVERED"))
            .associateBy { it.getProductId() }

        assertThat(result.keys).containsExactlyInAnyOrder(100L, 200L)
        assertThat(result.getValue(100L).getTotalQuantity()).isEqualTo(5L)
        assertThat(result.getValue(100L).getTotalAmount()).isEqualByComparingTo(BigDecimal("5000"))
        assertThat(result.getValue(200L).getTotalQuantity()).isEqualTo(1L)
        assertThat(result.getValue(200L).getTotalAmount()).isEqualByComparingTo(BigDecimal("2500"))
    }

    @Test
    fun 판매자의_배송_완료_항목은_기간_안에_완료된_주문의_것만_조회한다() {
        val from = LocalDateTime.of(2026, 3, 1, 0, 0)
        val to = LocalDateTime.of(2026, 3, 31, 23, 59, 59)
        val inPeriod = order("DELIVERED", updatedAt = LocalDateTime.of(2026, 3, 15, 12, 0))
        val beforePeriod = order("DELIVERED", updatedAt = LocalDateTime.of(2026, 2, 28, 23, 59, 59))
        val notDelivered = order("SHIPPED", updatedAt = LocalDateTime.of(2026, 3, 15, 12, 0))
        val mine = item(inPeriod, sellerId = SELLER_ID, productId = 100L, price = "1000", quantity = 1)
        item(inPeriod, sellerId = OTHER_SELLER_ID, productId = 200L, price = "2500", quantity = 1)
        item(inPeriod, sellerId = SELLER_ID, productId = 300L, price = "500", quantity = 1, deleted = true)
        item(beforePeriod, sellerId = SELLER_ID, productId = 100L, price = "1000", quantity = 1)
        item(notDelivered, sellerId = SELLER_ID, productId = 100L, price = "1000", quantity = 1)

        val result = orderItemRepository.findDeliveredItemsBySellerAndPeriod(SELLER_ID, from, to)

        assertThat(result.map { it.id }).containsExactly(mine)
    }

    private fun order(status: String, updatedAt: LocalDateTime = LocalDateTime.now(), deleted: Boolean = false): Long {
        val id = orderRepository.saveAndFlush(Order.create(buyerId = 1L, totalAmount = BigDecimal("1000"))).id!!
        jdbcTemplate.update(
            "UPDATE orders SET status = ?, updated_at = ?, deleted_at = ? WHERE id = ?",
            status, updatedAt, if (deleted) LocalDateTime.now() else null, id
        )
        return id
    }

    private fun item(
        orderId: Long,
        productId: Long,
        price: String,
        quantity: Int,
        sellerId: Long = SELLER_ID,
        deleted: Boolean = false
    ): Long {
        val id = orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = orderId,
                sellerId = sellerId,
                productId = productId,
                productName = "상품-$productId",
                productPrice = BigDecimal(price),
                quantity = quantity
            )
        ).id!!
        if (deleted) jdbcTemplate.update("UPDATE order_items SET deleted_at = ? WHERE id = ?", LocalDateTime.now(), id)
        return id
    }

    companion object {
        private const val SELLER_ID = 7L
        private const val OTHER_SELLER_ID = 8L
    }
}
