package com.hoppingmall.order.internal

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.enum.RefundReason
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
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("정산용 완료 환불 조회 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(InternalOrderController::class)
class SettlementCompletedRefundMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var internalOrderController: InternalOrderController

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var refundRepository: RefundRepository

    @Autowired
    private lateinit var refundItemRepository: RefundItemRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM refund_items")
        jdbcTemplate.update("DELETE FROM refunds")
        jdbcTemplate.update("DELETE FROM shippings")
    }

    @Test
    fun 배송_전에_전체_환불된_주문의_환불은_차감하지_않는다() {
        val orderId = order("REFUNDED", updatedAt = JAN_10)
        val itemId = item(orderId, BigDecimal("10000"), 1)
        completedRefund(orderId, completedAt = JAN_10, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
    }

    @Test
    fun 배송_중에_전체_환불된_주문의_환불은_차감하지_않는다() {
        val orderId = order("REFUNDED", updatedAt = JAN_10)
        shipping(orderId, "IN_TRANSIT", updatedAt = JAN_5)
        val itemId = item(orderId, BigDecimal("10000"), 1)
        completedRefund(orderId, completedAt = JAN_10, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
    }

    @Test
    fun 배송_전_부분_환불은_뒤에_배송이_완료된_기간에_차감한다() {
        val orderId = order("DELIVERED", updatedAt = FEB_5)
        shipping(orderId, "DELIVERED", updatedAt = FEB_5)
        val itemId = item(orderId, BigDecimal("10000"), 3)
        val refundId = completedRefund(orderId, completedAt = JAN_10, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
        assertThat(deductions(FEB_FROM, FEB_TO)).containsExactly(refundId to BigDecimal("10000.00"))
    }

    @Test
    fun 배송_전_부분_환불_뒤_배송되지_않은_주문의_환불은_차감하지_않는다() {
        val orderId = order("PAID", updatedAt = JAN_10)
        val itemId = item(orderId, BigDecimal("10000"), 3)
        completedRefund(orderId, completedAt = JAN_10, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
    }

    @Test
    fun 배송_전_부분_환불_뒤_나머지를_전체_환불한_주문의_환불은_둘_다_차감하지_않는다() {
        val orderId = order("REFUNDED", updatedAt = JAN_20)
        shipping(orderId, "PREPARING", updatedAt = JAN_5)
        val itemId = item(orderId, BigDecimal("10000"), 3)
        completedRefund(orderId, completedAt = JAN_10, itemId to 1)
        completedRefund(orderId, completedAt = JAN_20, itemId to 2)

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
    }

    @Test
    fun 배송_완료_뒤_부분_환불은_환불_기간에_환불된_수량의_정가로_차감한다() {
        val orderId = order("DELIVERED", updatedAt = JAN_5)
        shipping(orderId, "DELIVERED", updatedAt = JAN_5)
        val itemId = item(orderId, BigDecimal("10000"), 3)
        val refundId = completedRefund(orderId, completedAt = JAN_10, itemId to 2)

        assertThat(deductions(JAN_FROM, JAN_TO)).containsExactly(refundId to BigDecimal("20000.00"))
    }

    @Test
    fun 배송_완료_뒤_전체_환불은_환불_기간에_차감한다() {
        val orderId = order("REFUNDED", updatedAt = JAN_10)
        shipping(orderId, "DELIVERED", updatedAt = JAN_5)
        val itemId = item(orderId, BigDecimal("10000"), 1)
        val refundId = completedRefund(orderId, completedAt = JAN_10, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).containsExactly(refundId to BigDecimal("10000.00"))
    }

    @Test
    fun 배송_행_없이_배송_완료로_바뀐_뒤_전체_환불된_주문의_환불은_차감하지_않는다() {
        val orderId = order("REFUNDED", updatedAt = JAN_10)
        val itemId = item(orderId, BigDecimal("10000"), 1)
        completedRefund(orderId, completedAt = JAN_10, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
    }

    @Test
    fun 배송_행_없이_배송_완료로_바뀐_뒤_부분_환불은_차감한다() {
        val orderId = order("DELIVERED", updatedAt = JAN_5)
        val itemId = item(orderId, BigDecimal("10000"), 2)
        val refundId = completedRefund(orderId, completedAt = JAN_10, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).containsExactly(refundId to BigDecimal("10000.00"))
    }

    @Test
    fun 완료되지_않은_환불은_차감하지_않는다() {
        val orderId = order("DELIVERED", updatedAt = JAN_5)
        shipping(orderId, "DELIVERED", updatedAt = JAN_5)
        val itemId = item(orderId, BigDecimal("10000"), 3)
        listOf("REQUESTED", "APPROVED", "REJECTED").forEach { status ->
            refund(orderId, status = status, completedAt = null, refundAmount = BigDecimal("10000"), itemId to 1)
        }

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
    }

    @Test
    fun 판매가_잡힌_기간_뒤에_완료된_환불은_이미_지난_기간으로_되돌아가지_않는다() {
        val orderId = order("DELIVERED", updatedAt = JAN_20)
        shipping(orderId, "DELIVERED", updatedAt = JAN_20)
        val itemId = item(orderId, BigDecimal("10000"), 2)
        val refundId = completedRefund(orderId, completedAt = FEB_5, itemId to 1)

        assertThat(deductions(JAN_FROM, JAN_TO)).isEmpty()
        assertThat(deductions(FEB_FROM, FEB_TO)).containsExactly(refundId to BigDecimal("10000.00"))
    }

    @Test
    fun 쿠폰을_쓴_주문의_차감_금액은_구매자_환불액이_아니라_환불된_수량의_정가다() {
        val orderId = order("DELIVERED", updatedAt = JAN_5)
        shipping(orderId, "DELIVERED", updatedAt = JAN_5)
        val itemId = item(orderId, BigDecimal("10000"), 3)
        val refundId = refund(orderId, status = "COMPLETED", completedAt = JAN_10, refundAmount = BigDecimal("13000"), itemId to 2)

        assertThat(deductions(JAN_FROM, JAN_TO)).containsExactly(refundId to BigDecimal("20000.00"))
        assertThat(storedRefundAmount(refundId)).isEqualByComparingTo("13000")
    }

    private fun deductions(from: LocalDateTime, to: LocalDateTime): List<Pair<Long, BigDecimal>> =
        internalOrderController.getCompletedRefunds(SELLER_ID, from, to).body!!
            .map { it.id to it.refundAmount.setScale(2) }

    private fun order(status: String, updatedAt: LocalDateTime): Long {
        val id = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("30000"))).id!!
        jdbcTemplate.update("UPDATE orders SET status = ?, updated_at = ? WHERE id = ?", status, updatedAt, id)
        return id
    }

    private fun shipping(orderId: Long, status: String, updatedAt: LocalDateTime) {
        val id = shippingRepository.saveAndFlush(
            Shipping.create(
                orderId = orderId, buyerId = BUYER_ID, carrierName = "CJ",
                trackingNumber = "TRK-$orderId", recipientName = "홍길동",
                recipientPhone = "010-0000-0000", recipientAddress = "서울"
            )
        ).id!!
        jdbcTemplate.update("UPDATE shippings SET status = ?, updated_at = ? WHERE id = ?", status, updatedAt, id)
    }

    private fun item(orderId: Long, price: BigDecimal, quantity: Int): Long =
        orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = 100L,
                productName = "상품", productPrice = price, quantity = quantity
            )
        ).id!!

    private fun completedRefund(orderId: Long, completedAt: LocalDateTime, vararg items: Pair<Long, Int>): Long {
        val amount = items.fold(BigDecimal.ZERO) { acc, (orderItemId, quantity) ->
            acc.add(orderItemRepository.findById(orderItemId).get().productPrice.multiply(BigDecimal(quantity)))
        }
        return refund(orderId, status = "COMPLETED", completedAt = completedAt, refundAmount = amount, *items)
    }

    private fun refund(
        orderId: Long,
        status: String,
        completedAt: LocalDateTime?,
        refundAmount: BigDecimal,
        vararg items: Pair<Long, Int>
    ): Long {
        val refundId = refundRepository.saveAndFlush(
            Refund.create(
                orderId = orderId, paymentId = 900L, buyerId = BUYER_ID, sellerId = SELLER_ID,
                reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
                refundAmount = refundAmount, isFullRefund = false
            )
        ).id!!
        items.forEach { (orderItemId, quantity) ->
            val orderItem = orderItemRepository.findById(orderItemId).get()
            refundItemRepository.saveAndFlush(
                RefundItem.create(
                    refundId = refundId, orderItemId = orderItemId, productId = orderItem.productId,
                    productName = orderItem.productName, productPrice = orderItem.productPrice, quantity = quantity
                )
            )
        }
        jdbcTemplate.update("UPDATE refunds SET status = ?, completed_at = ? WHERE id = ?", status, completedAt, refundId)
        return refundId
    }

    private fun storedRefundAmount(refundId: Long): BigDecimal =
        jdbcTemplate.queryForObject("SELECT refund_amount FROM refunds WHERE id = ?", BigDecimal::class.java, refundId)!!

    companion object {
        private const val SELLER_ID = 7L
        private const val BUYER_ID = 1L
        private val JAN_FROM = LocalDateTime.of(2026, 1, 1, 0, 0)
        private val JAN_TO = LocalDateTime.of(2026, 1, 31, 23, 59, 59)
        private val FEB_FROM = LocalDateTime.of(2026, 2, 1, 0, 0)
        private val FEB_TO = LocalDateTime.of(2026, 2, 28, 23, 59, 59)
        private val JAN_5 = LocalDateTime.of(2026, 1, 5, 12, 0)
        private val JAN_10 = LocalDateTime.of(2026, 1, 10, 12, 0)
        private val JAN_20 = LocalDateTime.of(2026, 1, 20, 12, 0)
        private val FEB_5 = LocalDateTime.of(2026, 2, 5, 12, 0)
    }
}
