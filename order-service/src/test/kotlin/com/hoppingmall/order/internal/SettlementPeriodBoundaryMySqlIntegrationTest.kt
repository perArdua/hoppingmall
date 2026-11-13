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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("정산용 조회의 하루 경계 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(InternalOrderController::class)
class SettlementPeriodBoundaryMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var internalOrderController: InternalOrderController

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

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
    fun 그날_23시_59분_59초_5에_배송_완료된_주문은_그날에만_잡힌다() {
        val itemId = deliveredItem(deliveredAtKst = DAY.atTime(23, 59, 59, 500_000_000))

        assertThat(deliveredItemIds(DAY)).containsExactly(itemId)
        assertThat(deliveredItemIds(NEXT_DAY)).isEmpty()
    }

    @Test
    fun 다음_날_00시_정각에_배송_완료된_주문은_다음_날에만_잡힌다() {
        val itemId = deliveredItem(deliveredAtKst = NEXT_DAY.atStartOfDay())

        assertThat(deliveredItemIds(DAY)).isEmpty()
        assertThat(deliveredItemIds(NEXT_DAY)).containsExactly(itemId)
    }

    @Test
    fun 다음_날_00시_정각에_완료된_환불은_다음_날에만_차감한다() {
        val refundId = refundCompletedAt(completedAtKst = NEXT_DAY.atStartOfDay())

        assertThat(refundIds(DAY)).isEmpty()
        assertThat(refundIds(NEXT_DAY)).containsExactly(refundId)
    }

    @Test
    fun 그날_23시_59분_59초_5에_완료된_환불은_그날에만_차감한다() {
        val refundId = refundCompletedAt(completedAtKst = DAY.atTime(23, 59, 59, 500_000_000))

        assertThat(refundIds(DAY)).containsExactly(refundId)
        assertThat(refundIds(NEXT_DAY)).isEmpty()
    }

    @Test
    fun 하루씩_이어_붙인_기간에서_각_주문은_빠지거나_겹치지_않고_한_번씩만_잡힌다() {
        val instants = listOf(
            DAY.atStartOfDay(),
            DAY.atTime(12, 0),
            DAY.atTime(23, 59, 59, 999_999_000),
            NEXT_DAY.atStartOfDay(),
            NEXT_DAY.atTime(0, 0, 0, 1_000),
            NEXT_DAY.atTime(23, 59, 59, 999_999_000)
        )
        val itemIds = instants.map { deliveredItem(deliveredAtKst = it) }

        val found = listOf(DAY.minusDays(1), DAY, NEXT_DAY, NEXT_DAY.plusDays(1)).flatMap { deliveredItemIds(it) }

        assertThat(found).containsExactlyInAnyOrderElementsOf(itemIds)
    }

    private fun deliveredItemIds(day: LocalDate): List<Long> =
        internalOrderController.getDeliveredOrderItems(SELLER_ID, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
            .body!!.map { it.id }

    private fun refundIds(day: LocalDate): List<Long> =
        internalOrderController.getCompletedRefunds(SELLER_ID, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
            .body!!.map { it.id }

    private fun deliveredItem(deliveredAtKst: LocalDateTime): Long {
        val orderId = order(updatedAt = appTime(deliveredAtKst))
        return item(orderId)
    }

    private fun refundCompletedAt(completedAtKst: LocalDateTime): Long {
        val orderId = order(updatedAt = appTime(DAY.atTime(9, 0)))
        val itemId = item(orderId)
        val refundId = refundRepository.saveAndFlush(
            Refund.create(
                orderId = orderId, paymentId = 900L, buyerId = BUYER_ID, sellerId = SELLER_ID,
                reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
                refundAmount = PRICE, isFullRefund = false
            )
        ).id!!
        refundItemRepository.saveAndFlush(
            RefundItem.create(
                refundId = refundId, orderItemId = itemId, productId = 100L,
                productName = "상품", productPrice = PRICE, quantity = 1
            )
        )
        jdbcTemplate.update(
            "UPDATE refunds SET status = 'COMPLETED', completed_at = ? WHERE id = ?",
            appTime(completedAtKst), refundId
        )
        return refundId
    }

    private fun order(updatedAt: LocalDateTime): Long {
        val id = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = PRICE)).id!!
        jdbcTemplate.update("UPDATE orders SET status = 'DELIVERED', updated_at = ? WHERE id = ?", updatedAt, id)
        return id
    }

    private fun item(orderId: Long): Long =
        orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = 100L,
                productName = "상품", productPrice = PRICE, quantity = 1
            )
        ).id!!

    private fun appTime(kst: LocalDateTime): LocalDateTime =
        kst.atZone(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()

    companion object {
        private const val SELLER_ID = 841L
        private const val BUYER_ID = 3L
        private val PRICE = BigDecimal("10000")
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private val DAY: LocalDate = LocalDate.of(2026, 11, 14)
        private val NEXT_DAY: LocalDate = DAY.plusDays(1)
    }
}
