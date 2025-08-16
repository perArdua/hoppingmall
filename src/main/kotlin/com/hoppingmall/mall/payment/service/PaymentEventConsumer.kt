package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.global.common.config.DeadLetterMessage
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PaymentEventConsumer {
    
    private val logger = LoggerFactory.getLogger(PaymentEventConsumer::class.java)
    
    @KafkaListener(topics = ["\${kafka.topics.payment:payment}"], groupId = "payment-consumer-group")
    fun handlePaymentEvent(paymentEvent: PaymentEvent) {
        try {
            logger.info("결제 이벤트 처리 시작: ${paymentEvent.orderId}")
            
            processPayment(paymentEvent)
            
            logger.info("결제 이벤트 처리 완료: ${paymentEvent.orderId}")
            
        } catch (e: Exception) {
            logger.error("결제 이벤트 처리 실패: ${paymentEvent.orderId}, 오류: ${e.message}")
            throw e
        }
    }
    
    private fun processPayment(paymentEvent: PaymentEvent) {
        when (paymentEvent.paymentType) {
            PaymentType.CREDIT_CARD -> processCreditCardPayment(paymentEvent)
            PaymentType.BANK_TRANSFER -> processBankTransferPayment(paymentEvent)
            PaymentType.VIRTUAL_ACCOUNT -> processVirtualAccountPayment(paymentEvent)
        }
    }
    
    private fun processCreditCardPayment(paymentEvent: PaymentEvent) {
        if (paymentEvent.amount > 1000000) {
            throw RuntimeException("대금액 결제는 별도 승인이 필요합니다")
        }
        logger.info("신용카드 결제 처리: ${paymentEvent.orderId}")
    }
    
    private fun processBankTransferPayment(paymentEvent: PaymentEvent) {
        logger.info("계좌이체 결제 처리: ${paymentEvent.orderId}")
    }
    
    private fun processVirtualAccountPayment(paymentEvent: PaymentEvent) {
        logger.info("가상계좌 결제 처리: ${paymentEvent.orderId}")
    }
}

data class PaymentEvent(
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val paymentType: PaymentType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PaymentType {
    CREDIT_CARD,
    BANK_TRANSFER,
    VIRTUAL_ACCOUNT
}
