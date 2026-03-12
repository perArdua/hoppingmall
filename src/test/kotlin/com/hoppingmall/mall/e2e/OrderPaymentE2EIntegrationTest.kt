package com.hoppingmall.mall.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.inventory.domain.Inventory
import com.hoppingmall.mall.inventory.domain.repository.InventoryRepository
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.CompensationEventLogRepository
import com.hoppingmall.mall.payment.domain.repository.PaymentEventLogRepository
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.product.domain.ProductStatistics
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.mall.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("주문→결제→환불 E2E 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderPaymentE2EIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var paymentEventLogRepository: PaymentEventLogRepository

    @Autowired
    private lateinit var compensationEventLogRepository: CompensationEventLogRepository

    @Autowired
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @MockBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Test
    fun 주문_생성_후_결제_성공_시_포인트_적립과_알림이_발생한다() {
        val order = orderRepository.save(
            Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"))
        )
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)

        inventoryRepository.save(
            Inventory.create(productId = 100L, stockQuantity = 50)
        )

        orderItemRepository.save(
            OrderItem.create(
                orderId = order.id!!,
                sellerId = 2L,
                productId = 100L,
                productName = "테스트 상품",
                productPrice = BigDecimal("25000"),
                quantity = 2
            )
        )

        val paymentCompletedEvent = PaymentCompletedEvent(
            paymentId = 1L,
            orderId = order.id!!,
            userId = 1L,
            amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"),
            method = PaymentMethod.CREDIT_CARD,
            status = PaymentStatus.SUCCESS,
            transactionId = "tx-e2e-happy-001",
            completedAt = LocalDateTime.now()
        )
        kafkaTemplate.send("payment", order.id.toString(), paymentCompletedEvent)

        awaitUntil {
            paymentEventLogRepository.existsByTransactionId("tx-e2e-happy-001")
        }

        val pointEarnEvent = PointEarnRequestEvent(
            eventId = "point-e2e-happy-001",
            userId = 1L,
            orderId = order.id!!,
            paymentId = 1L,
            earnAmount = BigDecimal("500"),
            reason = "결제 완료"
        )
        kafkaTemplate.send("point-earn-request", "1", pointEarnEvent)

        awaitUntil {
            pointHistoryRepository.existsByEventId("point-e2e-happy-001")
        }

        val notificationEvent = NotificationEvent(
            eventId = "notif-e2e-happy-001",
            userId = 1L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제 완료",
            content = "주문번호 ${order.id}의 결제가 완료되었습니다",
            metadata = """{"orderId": ${order.id}}"""
        )
        kafkaTemplate.send("notification", "1", notificationEvent)

        awaitUntil {
            notificationRepository.existsByEventId("notif-e2e-happy-001")
        }

        val savedOrder = orderRepository.findById(order.id!!).get()
        assertThat(savedOrder.status).isEqualTo(OrderStatus.PAID)

        val savedLog = paymentEventLogRepository.findAll()
            .find { it.transactionId == "tx-e2e-happy-001" }
        assertThat(savedLog).isNotNull

        val point = pointRepository.findByUserId(1L)
        assertThat(point).isNotNull
        assertThat(point!!.balance).isEqualByComparingTo(BigDecimal("500"))

        val histories = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(1L)
        assertThat(histories).hasSize(1)
        assertThat(histories[0].type).isEqualTo(PointType.EARN)
        assertThat(histories[0].amount).isEqualByComparingTo(BigDecimal("500"))

        val notifications = notificationRepository.findByUserId(1L)
        assertThat(notifications).isNotEmpty
        assertThat(notifications.any { it.type == NotificationType.PAYMENT_COMPLETED }).isTrue()
    }

    @Test
    fun 결제_실패_시_주문이_취소되고_재고가_복구된다() {
        val order = orderRepository.save(
            Order.create(buyerId = 10L, totalAmount = BigDecimal("45000"))
        )
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)

        inventoryRepository.save(
            Inventory.create(productId = 200L, stockQuantity = 7)
        )

        orderItemRepository.save(
            OrderItem.create(
                orderId = order.id!!,
                sellerId = 3L,
                productId = 200L,
                productName = "테스트 상품2",
                productPrice = BigDecimal("15000"),
                quantity = 3
            )
        )

        val message = objectMapper.writeValueAsString(
            mapOf(
                "eventType" to "PaymentFailed",
                "eventId" to "comp-e2e-fail-001",
                "paymentId" to 100L,
                "orderId" to order.id!!,
                "userId" to 10L,
                "amount" to "45000",
                "reason" to "잔액 부족"
            )
        )

        kafkaTemplate.send("payment-compensation", message)

        awaitUntil {
            compensationEventLogRepository.existsByEventId("comp-e2e-fail-001")
        }

        val updatedOrder = orderRepository.findById(order.id!!).get()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.CANCELLED)

        val updatedInventory = inventoryRepository.findByProductId(200L)
        assertThat(updatedInventory).isNotNull
        assertThat(updatedInventory!!.stockQuantity).isEqualTo(10)
    }

    @Test
    fun 환불_완료_시_재고와_포인트가_복구된다() {
        val order = orderRepository.save(
            Order.create(buyerId = 20L, totalAmount = BigDecimal("20000"))
        )
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)

        val payment = paymentRepository.save(
            Payment.create(
                orderId = order.id!!,
                userId = 20L,
                amount = BigDecimal("20000"),
                method = PaymentMethod.CREDIT_CARD
            )
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "tx-e2e-refund-001", completedAt = LocalDateTime.now())
        paymentRepository.save(payment)

        inventoryRepository.save(
            Inventory.create(productId = 300L, stockQuantity = 48)
        )

        orderItemRepository.save(
            OrderItem.create(
                orderId = order.id!!,
                sellerId = 4L,
                productId = 300L,
                productName = "환불 테스트 상품",
                productPrice = BigDecimal("10000"),
                quantity = 2
            )
        )

        pointRepository.save(Point(userId = 20L, balance = BigDecimal("500")))
        pointHistoryRepository.save(
            PointHistory(
                userId = 20L,
                amount = BigDecimal("500"),
                type = PointType.EARN,
                reason = "결제 완료",
                orderId = order.id!!,
                paymentId = payment.id!!,
                eventId = "earn-for-refund-e2e"
            )
        )

        productStatisticsRepository.save(
            ProductStatistics.create(
                productId = 300L,
                productName = "환불 테스트 상품",
                sellerId = 4L,
                categoryId = 1L,
                totalSalesQuantity = 10,
                totalSalesAmount = BigDecimal("100000"),
                currentStock = 48
            )
        )

        val message = objectMapper.writeValueAsString(
            mapOf(
                "eventType" to "RefundCompleted",
                "eventData" to mapOf(
                    "eventId" to "refund-e2e-001",
                    "refundId" to 1L,
                    "orderId" to order.id!!,
                    "paymentId" to payment.id!!,
                    "buyerId" to 20L,
                    "refundAmount" to "20000",
                    "pointRefundAmount" to "500",
                    "isFullRefund" to true,
                    "couponId" to null,
                    "items" to listOf(
                        mapOf(
                            "productId" to 300L,
                            "quantity" to 2,
                            "refundPrice" to "10000"
                        )
                    )
                )
            )
        )

        kafkaTemplate.send("refund-completion", message)

        awaitUntil {
            refundEventLogRepository.existsByEventId("refund-e2e-001")
        }

        val updatedInventory = inventoryRepository.findByProductId(300L)
        assertThat(updatedInventory).isNotNull
        assertThat(updatedInventory!!.stockQuantity).isEqualTo(50)

        val updatedPoint = pointRepository.findByUserId(20L)
        assertThat(updatedPoint).isNotNull
        assertThat(updatedPoint!!.balance).isEqualByComparingTo(BigDecimal("1000"))

        val refundHistory = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(20L)
        assertThat(refundHistory.any { it.type == PointType.REFUND }).isTrue()

        val stats = productStatisticsRepository.findByProductId(300L)
        assertThat(stats).isNotNull
        assertThat(stats!!.totalRefundQuantity).isEqualTo(2)

        val updatedPayment = paymentRepository.findById(payment.id!!).get()
        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.REFUNDED)

        val updatedOrder = orderRepository.findById(order.id!!).get()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.CANCELLED)
    }
}
