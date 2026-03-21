package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.domain.PaymentEventLog
import com.hoppingmall.payment.payment.domain.repository.PaymentEventLogRepository
import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.service.strategy.PaymentMethodProcessorRegistry
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PaymentEventConsumer(
    private val paymentEventLogRepository: PaymentEventLogRepository,
    private val paymentMethodProcessorRegistry: PaymentMethodProcessorRegistry,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(PaymentEventConsumer::class.java)

    @KafkaListener(topics = ["\${kafka.topics.payment:payment}"], groupId = "payment-consumer-group")
    fun handlePaymentEvent(message: String) {
        val paymentEvent = objectMapper.readValue(message, PaymentCompletedEvent::class.java)
        try {
            if (paymentEventLogRepository.existsByTransactionId(paymentEvent.transactionId)) {
                logger.info("이미 처리된 결제 이벤트: ${paymentEvent.orderId}")
                return
            }

            try {
                paymentEventLogRepository.save(
                    PaymentEventLog(
                        transactionId = paymentEvent.transactionId,
                        paymentId = paymentEvent.paymentId,
                        orderId = paymentEvent.orderId
                    )
                )
            } catch (e: DataIntegrityViolationException) {
                logger.info("이미 처리된 결제 이벤트: ${paymentEvent.orderId}")
                return
            }

            logger.info("결제 이벤트 처리 시작: ${paymentEvent.orderId}")

            processPayment(paymentEvent)

            logger.info("결제 이벤트 처리 완료: ${paymentEvent.orderId}")

        } catch (e: Exception) {
            logger.error("결제 이벤트 처리 실패: ${paymentEvent.orderId}, 오류: ${e.message}")
            throw e
        }
    }

    private fun processPayment(paymentEvent: PaymentCompletedEvent) {
        val processor = paymentMethodProcessorRegistry.getProcessor(paymentEvent.method)
        processor.process(paymentEvent)
    }
}
