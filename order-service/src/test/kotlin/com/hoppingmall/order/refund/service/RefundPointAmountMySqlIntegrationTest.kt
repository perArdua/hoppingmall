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

@DisplayName("포인트를 쓴 주문의 환불 포인트 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(RefundCommandServiceImpl::class)
class RefundPointAmountMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var refundCommandService: RefundCommandServiceImpl

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

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
    fun 포인트_3천을_쓴_주문을_두_번에_나눠_환불하면_되돌릴_포인트_합이_쓴_포인트와_같다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 1, BigDecimal("20000") to 1)
        givenPayment(orderId, cashAmount = BigDecimal("27000"), pointAmount = BigDecimal("3000"))

        refund(orderId, itemIds[0] to 1)
        refund(orderId, itemIds[1] to 1)

        val events = publishedEvents(2)
        assertThat(events.map { it.pointRefundAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("1000"), BigDecimal("2000"))
        assertThat(events.map { it.isFullRefund }).containsExactly(false, true)
        assertThat(storedPointRefundSum(orderId)).isEqualByComparingTo("3000")
    }

    @Test
    fun 세_번에_나눈_환불의_포인트는_내림하고_끝수는_마지막_환불이_가져간다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 3)
        givenPayment(orderId, cashAmount = BigDecimal("29000"), pointAmount = BigDecimal("1000"))

        refund(orderId, itemIds[0] to 1)
        refund(orderId, itemIds[0] to 1)
        refund(orderId, itemIds[0] to 1)

        val events = publishedEvents(3)
        assertThat(events.map { it.pointRefundAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("333"), BigDecimal("333"), BigDecimal("334"))
        assertThat(events.map { it.refundAmount }.reduce(BigDecimal::add)).isEqualByComparingTo("29000")
        assertThat(storedPointRefundSum(orderId)).isEqualByComparingTo("1000")
    }

    @Test
    fun 전부_포인트로_낸_주문을_나눠_환불해도_항목_몫만큼_포인트를_되돌린다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 2)
        givenPayment(orderId, cashAmount = BigDecimal.ZERO, pointAmount = BigDecimal("20000"))

        refund(orderId, itemIds[0] to 1)
        refund(orderId, itemIds[0] to 1)

        val events = publishedEvents(2)
        assertThat(events.map { it.refundAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO)
        assertThat(events.map { it.pointRefundAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("10000"), BigDecimal("10000"))
    }

    @Test
    fun 포인트를_쓰지_않은_주문의_환불_포인트는_0이다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 2)
        givenPayment(orderId, cashAmount = BigDecimal("20000"), pointAmount = BigDecimal.ZERO)

        refund(orderId, itemIds[0] to 1)
        refund(orderId, itemIds[0] to 1)

        assertThat(publishedEvents(2).map { it.pointRefundAmount }).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO)
        assertThat(storedPointRefundSum(orderId)).isEqualByComparingTo("0")
    }

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

    private fun givenPayment(orderId: Long, cashAmount: BigDecimal, pointAmount: BigDecimal) {
        whenever(paymentQueryPort.findByOrderId(orderId)).thenReturn(
            PaymentInfo(
                id = PAYMENT_ID,
                orderId = orderId,
                amount = cashAmount,
                pointAmount = pointAmount,
                couponId = null,
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

    private fun storedPointRefundSum(orderId: Long): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(point_refund_amount), 0) FROM refunds WHERE order_id = ?",
            BigDecimal::class.java,
            orderId
        )!!

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val PAYMENT_ID = 900L
    }
}
