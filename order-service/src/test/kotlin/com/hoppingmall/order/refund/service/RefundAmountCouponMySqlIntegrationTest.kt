package com.hoppingmall.order.refund.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("쿠폰을 쓴 주문의 환불 금액 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(RefundCommandServiceImpl::class)
class RefundAmountCouponMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var refundCommandService: RefundCommandServiceImpl

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @MockitoBean
    private lateinit var paymentQueryPort: PaymentQueryPort

    @MockitoBean
    private lateinit var refundEventPublisher: RefundEventPublisher

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM refund_items")
        jdbcTemplate.update("DELETE FROM refunds")
        jdbcTemplate.update("DELETE FROM shippings")
    }

    @Test
    fun 만원_상품_1개를_3천원_쿠폰으로_산_주문을_전체_환불하면_환불_금액은_7천원이다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 1)
        givenPayment(orderId, paidAmount = BigDecimal("7000"), couponId = 1L)

        val response = refund(orderId, itemIds[0] to 1)

        assertThat(response.refundAmount).isEqualByComparingTo("7000")
        assertThat(response.isFullRefund).isTrue()
        assertThat(response.items.single().refundPrice).isEqualByComparingTo("7000")
        val event = publishedEvents(1).single()
        assertThat(event.refundAmount).isEqualByComparingTo("7000")
        assertThat(event.items.single().refundPrice).isEqualByComparingTo("7000")
    }

    @Test
    fun 항목_3개를_천원_쿠폰으로_산_주문을_세_번에_나눠_환불하면_환불_합계가_결제_금액과_같다() {
        val (orderId, itemIds) = saveOrder(
            BigDecimal("10000") to 1,
            BigDecimal("20000") to 1,
            BigDecimal("10000") to 3
        )
        val paidAmount = BigDecimal("59000")
        givenPayment(orderId, paidAmount = paidAmount, couponId = 1L)

        val first = refund(orderId, itemIds[0] to 1, itemIds[2] to 1)
        val second = refund(orderId, itemIds[2] to 1)
        val third = refund(orderId, itemIds[1] to 1, itemIds[2] to 1)

        val amounts = listOf(first, second, third).map { it.refundAmount }
        amounts.forEach {
            assertThat(it).isGreaterThan(BigDecimal.ZERO)
            assertThat(it).isLessThanOrEqualTo(paidAmount)
        }
        assertThat(amounts.reduce(BigDecimal::add)).isEqualByComparingTo(paidAmount)
        assertThat(listOf(first, second, third).map { it.isFullRefund }).containsExactly(false, false, true)
        listOf(first, second, third).forEach { response ->
            assertThat(response.items.map { it.refundPrice }.reduce(BigDecimal::add))
                .isEqualByComparingTo(response.refundAmount)
        }
        val events = publishedEvents(3)
        assertThat(events.map { it.refundAmount }.reduce(BigDecimal::add)).isEqualByComparingTo(paidAmount)
        assertThat(storedRefundPriceSum(orderId)).isEqualByComparingTo(paidAmount)
    }

    @Test
    fun 쿠폰_없는_주문의_환불_금액은_지금처럼_단가에_수량을_곱한_값이다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("25000") to 2)
        givenPayment(orderId, paidAmount = BigDecimal("50000"), couponId = null)

        val first = refund(orderId, itemIds[0] to 1)
        val second = refund(orderId, itemIds[0] to 1)

        assertThat(first.refundAmount).isEqualByComparingTo("25000")
        assertThat(second.refundAmount).isEqualByComparingTo("25000")
        assertThat(second.isFullRefund).isTrue()
    }

    @Test
    fun 쿠폰을_쓴_배송_완료_주문의_단순_변심_환불은_쿠폰_비례로_나눈_상품_금액에서_반품_배송비를_뺀다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 3)
        givenPayment(orderId, paidAmount = BigDecimal("27000"), couponId = 1L)
        val payment = PaymentInfo(
            id = PAYMENT_ID, orderId = orderId, amount = BigDecimal("27000"),
            pointAmount = BigDecimal.ZERO, couponId = 1L, status = "SUCCESS"
        )
        whenever(paymentQueryPort.findById(PAYMENT_ID)).thenReturn(payment)
        saveDeliveredShipping(orderId)

        val first = refundFor(orderId, RefundReason.CHANGE_OF_MIND, itemIds[0] to 1)
        refundCommandService.approveRefund(first.id, SELLER_ID)
        val second = refundFor(orderId, RefundReason.CHANGE_OF_MIND, itemIds[0] to 2)
        refundCommandService.approveRefund(second.id, SELLER_ID)

        assertThat(listOf(first, second).map { it.productAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("9000"), BigDecimal("18000"))
        assertThat(listOf(first, second).map { it.returnShippingFee }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("3000"), BigDecimal("3000"))
        assertThat(listOf(first, second).map { it.refundAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("6000"), BigDecimal("15000"))
        assertThat(second.isFullRefund).isTrue()
        assertThat(storedRefundPriceSum(orderId)).isEqualByComparingTo("27000")
        assertThat(storedColumnSum(orderId, "refund_amount")).isEqualByComparingTo("21000")
        assertThat(storedColumnSum(orderId, "return_shipping_fee")).isEqualByComparingTo("6000")
        assertThat(publishedEvents(2).map { it.refundAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("6000"), BigDecimal("15000"))
    }

    @Test
    fun 쿠폰을_쓴_배송_완료_주문의_하자_환불은_반품_배송비_없이_쿠폰_비례_금액을_돌려준다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 3)
        givenPayment(orderId, paidAmount = BigDecimal("27000"), couponId = 1L)
        saveDeliveredShipping(orderId)

        val response = refundFor(orderId, RefundReason.DEFECTIVE, itemIds[0] to 1)

        assertThat(response.productAmount).isEqualByComparingTo("9000")
        assertThat(response.returnShippingFee).isEqualByComparingTo("0")
        assertThat(response.refundAmount).isEqualByComparingTo("9000")
        assertThat(storedColumnSum(orderId, "return_shipping_fee")).isEqualByComparingTo("0")
    }

    private fun refundFor(orderId: Long, reason: RefundReason, vararg items: Pair<Long, Int>): RefundResponse =
        refundCommandService.requestRefund(
            BUYER_ID,
            RefundCreateRequest(
                orderId = orderId,
                reason = reason,
                items = items.map { (orderItemId, quantity) -> RefundItemRequest(orderItemId, quantity) }
            )
        )

    private fun saveDeliveredShipping(orderId: Long) {
        val shipping = Shipping.create(
            orderId = orderId, buyerId = BUYER_ID, carrierName = "택배", trackingNumber = "T-$orderId",
            recipientName = "구매자", recipientPhone = "010-0000-0000", recipientAddress = "주소"
        )
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(ShippingStatus.DELIVERED)
        shippingRepository.saveAndFlush(shipping)
        jdbcTemplate.update("UPDATE orders SET status = 'DELIVERED', delivered_at = ? WHERE id = ?", LocalDateTime.now(), orderId)
    }

    private fun storedColumnSum(orderId: Long, column: String): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM($column), 0) FROM refunds WHERE order_id = ?",
            BigDecimal::class.java,
            orderId
        )!!

    private fun refund(orderId: Long, vararg items: Pair<Long, Int>): RefundResponse =
        refundCommandService.requestRefund(
            BUYER_ID,
            RefundCreateRequest(
                orderId = orderId,
                reason = RefundReason.CHANGE_OF_MIND,
                items = items.map { (orderItemId, quantity) -> RefundItemRequest(orderItemId, quantity) }
            )
        )

    private fun publishedEvents(count: Int): List<RefundCompletedEvent> {
        val captor = argumentCaptor<RefundCompletedEvent>()
        verify(refundEventPublisher, times(count)).publishRefundCompletedEvent(captor.capture())
        return captor.allValues
    }

    private fun givenPayment(orderId: Long, paidAmount: BigDecimal, couponId: Long?) {
        whenever(paymentQueryPort.findByOrderId(orderId)).thenReturn(
            PaymentInfo(
                id = PAYMENT_ID,
                orderId = orderId,
                amount = paidAmount,
                pointAmount = BigDecimal.ZERO,
                couponId = couponId,
                status = "SUCCESS"
            )
        )
    }

    private fun saveOrder(vararg lines: Pair<BigDecimal, Int>): Pair<Long, List<Long>> {
        val totalAmount = lines.fold(BigDecimal.ZERO) { acc, (price, quantity) -> acc.add(price.multiply(BigDecimal(quantity))) }
        val orderId = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = totalAmount)).id!!
        val itemIds = lines.mapIndexed { index, (price, quantity) ->
            orderItemRepository.saveAndFlush(
                OrderItem.create(
                    orderId = orderId, sellerId = SELLER_ID, productId = 100L + index,
                    productName = "상품$index", productPrice = price, quantity = quantity
                )
            ).id!!
        }
        jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId)
        return orderId to itemIds
    }

    private fun storedRefundPriceSum(orderId: Long): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT SUM(ri.refund_price) FROM refund_items ri JOIN refunds r ON ri.refund_id = r.id WHERE r.order_id = ?",
            BigDecimal::class.java,
            orderId
        )!!

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val PAYMENT_ID = 900L
    }
}
