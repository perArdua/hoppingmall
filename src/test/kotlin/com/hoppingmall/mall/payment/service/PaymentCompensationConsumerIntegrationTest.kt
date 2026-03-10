package com.hoppingmall.mall.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.inventory.domain.Inventory
import com.hoppingmall.mall.inventory.domain.repository.InventoryRepository
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.payment.domain.repository.CompensationEventLogRepository
import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal

@DisplayName("PaymentCompensationConsumer 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentCompensationConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var compensationEventLogRepository: CompensationEventLogRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Test
    fun 결제_실패_시_주문을_취소하고_재고를_복구한다() {
        val order = orderRepository.save(
            Order.create(buyerId = 1L, totalAmount = BigDecimal("30000"))
        )
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)

        inventoryRepository.save(
            Inventory.create(productId = 500L, stockQuantity = 8)
        )

        orderItemRepository.save(
            OrderItem.create(
                orderId = order.id!!,
                sellerId = 1L,
                productId = 500L,
                productName = "테스트 상품",
                productPrice = BigDecimal("15000"),
                quantity = 2
            )
        )

        val message = objectMapper.writeValueAsString(
            mapOf(
                "eventType" to "PaymentFailed",
                "eventId" to "comp-fail-001",
                "paymentId" to 1L,
                "orderId" to order.id!!,
                "userId" to 1L,
                "amount" to "30000",
                "reason" to "잔액 부족"
            )
        )

        kafkaTemplate.send("payment-compensation", message)

        awaitUntil {
            compensationEventLogRepository.existsByEventId("comp-fail-001")
        }

        val updatedOrder = orderRepository.findById(order.id!!).get()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.CANCELLED)

        val updatedInventory = inventoryRepository.findByProductId(500L)
        assertThat(updatedInventory).isNotNull
        assertThat(updatedInventory!!.stockQuantity).isEqualTo(10)
    }

    @Test
    fun 결제_취소_시_포인트까지_반환한다() {
        val order = orderRepository.save(
            Order.create(buyerId = 2L, totalAmount = BigDecimal("20000"))
        )
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)

        inventoryRepository.save(
            Inventory.create(productId = 600L, stockQuantity = 5)
        )

        orderItemRepository.save(
            OrderItem.create(
                orderId = order.id!!,
                sellerId = 2L,
                productId = 600L,
                productName = "테스트 상품2",
                productPrice = BigDecimal("20000"),
                quantity = 1
            )
        )

        pointRepository.save(Point(userId = 2L, balance = BigDecimal("200")))

        pointHistoryRepository.save(
            PointHistory(
                userId = 2L,
                amount = BigDecimal("200"),
                type = PointType.EARN,
                reason = "결제 완료",
                orderId = order.id!!,
                paymentId = 10L,
                eventId = "earn-for-cancel-test"
            )
        )

        val message = objectMapper.writeValueAsString(
            mapOf(
                "eventType" to "PaymentCancelled",
                "eventId" to "comp-cancel-001",
                "paymentId" to 10L,
                "orderId" to order.id!!,
                "userId" to 2L,
                "amount" to "20000",
                "transactionId" to "tx-cancel-001"
            )
        )

        kafkaTemplate.send("payment-compensation", message)

        awaitUntil {
            compensationEventLogRepository.existsByEventId("comp-cancel-001")
        }

        val updatedOrder = orderRepository.findById(order.id!!).get()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.CANCELLED)

        val updatedInventory = inventoryRepository.findByProductId(600L)
        assertThat(updatedInventory!!.stockQuantity).isEqualTo(6)

        val updatedPoint = pointRepository.findByUserId(2L)
        assertThat(updatedPoint).isNotNull
        assertThat(updatedPoint!!.balance).isEqualByComparingTo(BigDecimal.ZERO)

        val refundHistory = pointHistoryRepository.findByPaymentIdAndType(10L, PointType.REFUND)
        assertThat(refundHistory).isNotNull
    }

    @Test
    fun 중복_보상_이벤트는_무시된다() {
        val order = orderRepository.save(
            Order.create(buyerId = 3L, totalAmount = BigDecimal("10000"))
        )
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)

        val message = objectMapper.writeValueAsString(
            mapOf(
                "eventType" to "PaymentFailed",
                "eventId" to "comp-dedup-001",
                "paymentId" to 3L,
                "orderId" to order.id!!,
                "userId" to 3L,
                "amount" to "10000",
                "reason" to "잔액 부족"
            )
        )

        kafkaTemplate.send("payment-compensation", message)

        awaitUntil {
            compensationEventLogRepository.existsByEventId("comp-dedup-001")
        }

        kafkaTemplate.send("payment-compensation", message)
        Thread.sleep(1000)

        val count = compensationEventLogRepository.findAll()
            .count { it.eventId == "comp-dedup-001" }
        assertThat(count).isEqualTo(1)
    }
}
