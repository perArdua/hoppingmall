package com.hoppingmall.mall.refund.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.point.service.PointCommandService
import com.hoppingmall.mall.product.service.ProductStatisticsCommandService
import com.hoppingmall.mall.refund.domain.RefundEventLog
import com.hoppingmall.mall.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.mall.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.mall.refund.dto.event.RefundItemEvent
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class RefundCompletionConsumer(
    private val refundEventLogRepository: RefundEventLogRepository,
    private val inventoryCommandService: InventoryCommandService,
    private val pointCommandService: PointCommandService,
    private val productStatisticsCommandService: ProductStatisticsCommandService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(RefundCompletionConsumer::class.java)

    @KafkaListener(topics = ["refund-completion"], groupId = "refund-completion-service")
    fun handleRefundCompletionEvent(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        when (eventType) {
            "RefundCompleted" -> {
                val eventData = node.get("eventData") ?: node
                val event = parseRefundCompletedEvent(eventData)
                processRefundCompletion(event)
            }
            else -> logger.warn("알 수 없는 환불 이벤트 타입: $eventType")
        }
    }

    private fun parseRefundCompletedEvent(node: com.fasterxml.jackson.databind.JsonNode): RefundCompletedEvent {
        val items = node.get("items")?.map { itemNode ->
            RefundItemEvent(
                productId = itemNode.get("productId").asLong(),
                quantity = itemNode.get("quantity").asInt(),
                refundPrice = BigDecimal(itemNode.get("refundPrice").asText())
            )
        } ?: emptyList()

        return RefundCompletedEvent(
            eventId = node.get("eventId").asText(),
            refundId = node.get("refundId").asLong(),
            orderId = node.get("orderId").asLong(),
            paymentId = node.get("paymentId").asLong(),
            buyerId = node.get("buyerId").asLong(),
            refundAmount = BigDecimal(node.get("refundAmount").asText()),
            pointRefundAmount = BigDecimal(node.get("pointRefundAmount").asText()),
            isFullRefund = node.get("isFullRefund").asBoolean(),
            items = items
        )
    }

    @Transactional
    fun processRefundCompletion(event: RefundCompletedEvent) {
        try {
            if (refundEventLogRepository.existsByEventId(event.eventId)) {
                logger.info("이미 처리된 환불 완료 이벤트: ${event.eventId}")
                return
            }

            event.items.forEach { item ->
                inventoryCommandService.increaseStock(item.productId, item.quantity)
            }

            pointCommandService.refundPoints(
                userId = event.buyerId,
                amount = event.pointRefundAmount,
                paymentId = event.paymentId,
                orderId = event.orderId
            )

            event.items.forEach { item ->
                productStatisticsCommandService.incrementRefundStats(
                    item.productId,
                    item.quantity.toLong(),
                    item.refundPrice
                )
            }

            if (event.isFullRefund) {
                val payment = paymentRepository.findById(event.paymentId).orElse(null)
                if (payment != null) {
                    payment.updateStatus(PaymentStatus.REFUNDED)
                    paymentRepository.save(payment)
                }

                val order = orderRepository.findById(event.orderId).orElse(null)
                if (order != null && order.isCancellable()) {
                    order.updateStatus(OrderStatus.CANCELLED)
                    orderRepository.save(order)
                }
            }

            try {
                refundEventLogRepository.save(
                    RefundEventLog(
                        eventId = event.eventId,
                        eventType = "REFUND_COMPLETED",
                        refundId = event.refundId,
                        orderId = event.orderId
                    )
                )
            } catch (e: DataIntegrityViolationException) {
                logger.info("이미 처리된 환불 완료 이벤트: ${event.eventId}")
                return
            }

            logger.info("환불 완료 처리 성공: refundId=${event.refundId}, orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("환불 완료 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }
}
