package com.hoppingmall.order.order.domain.repository

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("정산용 배송 완료 항목 조회의 환불된 주문 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderItemRefundedDeliveredMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM shippings")
    }

    @Test
    fun 배송_완료_뒤_전체_환불된_주문의_항목은_배송_완료_시각의_기간에_판매로_남는다() {
        val refundedAfterDelivery = order("REFUNDED", updatedAt = AFTER_PERIOD)
        shipping(refundedAfterDelivery, delivered = true, updatedAt = IN_PERIOD)
        val item = item(refundedAfterDelivery)

        val inPeriod = orderItemRepository.findDeliveredItemsBySellerAndPeriod(SELLER_ID, FROM, TO)
        val refundPeriod = orderItemRepository.findDeliveredItemsBySellerAndPeriod(
            SELLER_ID, AFTER_PERIOD.minusDays(1), AFTER_PERIOD.plusDays(1)
        )

        assertThat(inPeriod.map { it.id }).containsExactly(item)
        assertThat(refundPeriod).isEmpty()
    }

    @Test
    fun 배송되지_않은_채_전체_환불된_주문의_항목은_판매로_잡히지_않는다() {
        val refundedBeforeShipping = order("REFUNDED", updatedAt = IN_PERIOD)
        item(refundedBeforeShipping)
        val refundedWhilePreparing = order("REFUNDED", updatedAt = IN_PERIOD)
        shipping(refundedWhilePreparing, delivered = false, updatedAt = IN_PERIOD)
        item(refundedWhilePreparing)

        val result = orderItemRepository.findDeliveredItemsBySellerAndPeriod(SELLER_ID, FROM, TO)

        assertThat(result).isEmpty()
    }

    @Test
    fun 배송_완료된_주문의_항목은_지금처럼_주문_변경_시각으로_조회한다() {
        val delivered = order("DELIVERED", updatedAt = IN_PERIOD)
        shipping(delivered, delivered = true, updatedAt = BEFORE_PERIOD)
        val item = item(delivered)

        val result = orderItemRepository.findDeliveredItemsBySellerAndPeriod(SELLER_ID, FROM, TO)

        assertThat(result.map { it.id }).containsExactly(item)
    }

    private fun order(status: String, updatedAt: LocalDateTime): Long {
        val id = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("1000"))).id!!
        jdbcTemplate.update("UPDATE orders SET status = ?, updated_at = ? WHERE id = ?", status, updatedAt, id)
        return id
    }

    private fun shipping(orderId: Long, delivered: Boolean, updatedAt: LocalDateTime) {
        val id = shippingRepository.saveAndFlush(
            Shipping.create(
                orderId = orderId, buyerId = BUYER_ID, carrierName = "CJ",
                trackingNumber = "TRK-$orderId", recipientName = "홍길동",
                recipientPhone = "010-0000-0000", recipientAddress = "서울"
            )
        ).id!!
        val status = if (delivered) "DELIVERED" else "PREPARING"
        jdbcTemplate.update("UPDATE shippings SET status = ?, updated_at = ? WHERE id = ?", status, updatedAt, id)
    }

    private fun item(orderId: Long): Long =
        orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = 100L,
                productName = "상품", productPrice = BigDecimal("1000"), quantity = 1
            )
        ).id!!

    companion object {
        private const val SELLER_ID = 7L
        private const val BUYER_ID = 1L
        private val FROM = LocalDateTime.of(2026, 3, 1, 0, 0)
        private val TO = LocalDateTime.of(2026, 3, 31, 23, 59, 59)
        private val BEFORE_PERIOD = LocalDateTime.of(2026, 2, 20, 12, 0)
        private val IN_PERIOD = LocalDateTime.of(2026, 3, 15, 12, 0)
        private val AFTER_PERIOD = LocalDateTime.of(2026, 4, 10, 12, 0)
    }
}
