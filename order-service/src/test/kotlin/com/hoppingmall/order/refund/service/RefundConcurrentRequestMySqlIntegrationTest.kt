package com.hoppingmall.order.refund.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.support.OrderMySqlServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("같은 항목의 환불 요청 동시 처리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class RefundConcurrentRequestMySqlIntegrationTest : OrderMySqlServiceTestSupport() {

    @Autowired
    private lateinit var refundCommandService: RefundCommandServiceImpl

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var refundItemRepository: RefundItemRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM refund_items")
        jdbcTemplate.update("DELETE FROM refunds")
        jdbcTemplate.update("DELETE FROM shippings")
    }

    @Test
    fun 같은_항목에_동시에_온_두_환불_요청은_하나만_접수되고_다른_하나는_RFD004다() {
        val (orderId, itemId) = saveDeliveredOrder(quantity = 3)
        val start = CountDownLatch(1)

        val results = (1..2).map {
            executor.submit(Callable {
                start.await()
                runCatching { refund(orderId, itemId) }
            })
        }
        start.countDown()
        val outcomes = results.map { it.get(30, TimeUnit.SECONDS) }

        assertThat(outcomes.count { it.isSuccess }).isEqualTo(1)
        val failure = outcomes.single { it.isFailure }.exceptionOrNull()
        assertThat(failure).isInstanceOf(BusinessException::class.java)
        assertThat((failure as BusinessException).errorCode.code).isEqualTo("RFD004")
        assertThat(refundCount(orderId)).isEqualTo(1)
    }

    @Test
    fun 환불_요청은_다른_트랜잭션이_잡은_주문_행_잠금이_풀릴_때까지_기다린다() {
        val (orderId, itemId) = saveDeliveredOrder(quantity = 3)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = executor.submit {
            TransactionTemplate(transactionManager).executeWithoutResult {
                jdbcTemplate.queryForObject("SELECT id FROM orders WHERE id = ? FOR UPDATE", Long::class.java, orderId)
                locked.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }
        locked.await(10, TimeUnit.SECONDS)
        val enteredAt = AtomicLong()

        val pending = executor.submit(Callable {
            enteredAt.set(System.currentTimeMillis())
            refund(orderId, itemId)
        })
        awaitLockWait(pending, enteredAt, "환불 요청", "orders")
        assertThat(pending.isDone).isFalse()
        release.countDown()
        holder.get(30, TimeUnit.SECONDS)

        assertThat(pending.get(30, TimeUnit.SECONDS).items.single().orderItemId).isEqualTo(itemId)
        assertThat(refundCount(orderId)).isEqualTo(1)
    }

    @Test
    fun 상태별_환불_수량은_거절된_환불을_빼고_항목과_상태로_나눠_센다() {
        val (orderId, itemId) = saveDeliveredOrder(quantity = 3)
        val first = refund(orderId, itemId)
        jdbcTemplate.update("UPDATE refunds SET status = 'REJECTED' WHERE id = ?", first.id)
        val second = refund(orderId, itemId)
        jdbcTemplate.update("UPDATE refunds SET status = 'COMPLETED' WHERE id = ?", second.id)
        refund(orderId, itemId)

        val quantities = refundItemRepository.findRefundQuantitiesByStatus(orderId)
            .associate { it.status to (it.orderItemId to it.quantity) }

        assertThat(quantities).containsOnlyKeys("COMPLETED", "REQUESTED")
        assertThat(quantities["COMPLETED"]).isEqualTo(itemId to 1L)
        assertThat(quantities["REQUESTED"]).isEqualTo(itemId to 1L)
    }

    private fun refund(orderId: Long, itemId: Long): RefundResponse =
        refundCommandService.requestRefund(
            BUYER_ID,
            RefundCreateRequest(
                orderId = orderId,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(itemId, 1))
            )
        )

    private fun saveDeliveredOrder(quantity: Int): Pair<Long, Long> {
        val price = BigDecimal("10000")
        val total = price.multiply(BigDecimal(quantity))
        val orderId = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = total)).id!!
        val itemId = orderItemRepository.saveAndFlush(
            OrderItem.create(
                orderId = orderId, sellerId = SELLER_ID, productId = 100L,
                productName = "상품", productPrice = price, quantity = quantity
            )
        ).id!!
        val shipping = Shipping.create(
            orderId = orderId, buyerId = BUYER_ID, carrierName = "택배", trackingNumber = "T-$orderId",
            recipientName = "구매자", recipientPhone = "010-0000-0000", recipientAddress = "주소"
        )
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(ShippingStatus.DELIVERED)
        shippingRepository.saveAndFlush(shipping)
        jdbcTemplate.update(
            "UPDATE orders SET status = 'DELIVERED', delivered_at = ? WHERE id = ?", LocalDateTime.now(), orderId
        )
        whenever(paymentQueryPort.findByOrderId(orderId)).thenReturn(
            PaymentInfo(
                id = PAYMENT_ID, orderId = orderId, amount = total,
                pointAmount = BigDecimal.ZERO, couponId = null, status = "SUCCESS"
            )
        )
        return orderId to itemId
    }

    private fun refundCount(orderId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refunds WHERE order_id = ?", Int::class.java, orderId)!!

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val PAYMENT_ID = 900L
    }
}
