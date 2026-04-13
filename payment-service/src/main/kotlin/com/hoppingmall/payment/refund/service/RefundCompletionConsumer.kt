package com.hoppingmall.payment.refund.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.port.InventoryCommandPort
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.port.ProductStatisticsPort
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.payment.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.payment.refund.dto.event.RefundItemEvent
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RefundCompletionConsumer(
    private val refundEventLogRepository: RefundEventLogRepository,
    private val refundLocalOperationService: RefundLocalOperationService,
    private val inventoryCommandPort: InventoryCommandPort,
    private val orderCommandPort: OrderCommandPort,
    private val productStatisticsPort: ProductStatisticsPort,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(RefundCompletionConsumer::class.java)

    @KafkaListener(topics = [KafkaTopics.REFUND_COMPLETION], groupId = "refund-completion-service")
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
            couponId = node.get("couponId")?.takeIf { !it.isNull }?.asLong(),
            items = items
        )
    }

    fun processRefundCompletion(event: RefundCompletedEvent) {
        try {
            val existingLog = refundEventLogRepository.findByEventIdWithSteps(event.eventId)
            if (existingLog != null && existingLog.completedSteps.size >= 6) {
                logger.info("이미 완료된 환불 이벤트: ${event.eventId}")
                return
            }

            val eventLog = existingLog ?: createEventLog(event)

            refundLocalOperationService.execute(event, eventLog)
            executeInventoryOperations(event, eventLog)
            executeOrderOperations(event, eventLog)

            logger.info("환불 완료 처리 성공: refundId=${event.refundId}, orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("환불 완료 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }

    private fun createEventLog(event: RefundCompletedEvent): RefundEventLog {
        return try {
            refundEventLogRepository.save(
                RefundEventLog(
                    eventId = event.eventId,
                    eventType = "REFUND_COMPLETED",
                    refundId = event.refundId,
                    orderId = event.orderId
                )
            )
        } catch (e: DataIntegrityViolationException) {
            refundEventLogRepository.findByEventIdWithSteps(event.eventId)
                ?: throw e
        }
    }

    fun executeInventoryOperations(event: RefundCompletedEvent, eventLog: RefundEventLog) {
        if (!eventLog.isStepCompleted(RefundEventLog.INVENTORY_RESTORED)) {
            event.items.forEach { item ->
                inventoryCommandPort.increaseStock(item.productId, item.quantity)
            }
            refundLocalOperationService.markStepAndSave(event.eventId, RefundEventLog.INVENTORY_RESTORED)
            eventLog.markStepCompleted(RefundEventLog.INVENTORY_RESTORED)
        }

        if (!eventLog.isStepCompleted(RefundEventLog.STATS_UPDATED)) {
            event.items.forEach { item ->
                productStatisticsPort.incrementRefundStats(
                    item.productId,
                    item.quantity.toLong(),
                    item.refundPrice
                )
            }
            refundLocalOperationService.markStepAndSave(event.eventId, RefundEventLog.STATS_UPDATED)
            eventLog.markStepCompleted(RefundEventLog.STATS_UPDATED)
        }
    }

    fun executeOrderOperations(event: RefundCompletedEvent, eventLog: RefundEventLog) {
        if (event.isFullRefund && !eventLog.isStepCompleted(RefundEventLog.ORDER_CANCELLED)) {
            orderCommandPort.cancelOrder(event.orderId)
            refundLocalOperationService.markStepAndSave(event.eventId, RefundEventLog.ORDER_CANCELLED)
            eventLog.markStepCompleted(RefundEventLog.ORDER_CANCELLED)
        } else if (!event.isFullRefund) {
            refundLocalOperationService.markStepAndSave(event.eventId, RefundEventLog.ORDER_CANCELLED)
            eventLog.markStepCompleted(RefundEventLog.ORDER_CANCELLED)
        }
    }
}
