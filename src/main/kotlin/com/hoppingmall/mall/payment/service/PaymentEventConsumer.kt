package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.enum.PaymentMethod
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PaymentEventConsumer {
    
    private val logger = LoggerFactory.getLogger(PaymentEventConsumer::class.java)
    
    @KafkaListener(topics = ["\${kafka.topics.payment:payment}"], groupId = "payment-consumer-group")
    fun handlePaymentEvent(paymentEvent: PaymentCompletedEvent) {
        try {
            logger.info("결제 이벤트 처리 시작: ${paymentEvent.orderId}")
            
            processPayment(paymentEvent)
            
            logger.info("결제 이벤트 처리 완료: ${paymentEvent.orderId}")
            
        } catch (e: Exception) {
            logger.error("결제 이벤트 처리 실패: ${paymentEvent.orderId}, 오류: ${e.message}")
            throw e
        }
    }
    
    private fun processPayment(paymentEvent: PaymentCompletedEvent) {
        when (paymentEvent.method) {
            PaymentMethod.CREDIT_CARD -> processCreditCardPayment(paymentEvent)
            PaymentMethod.BANK_TRANSFER -> processBankTransferPayment(paymentEvent)
        }
    }
    
    private fun processCreditCardPayment(paymentEvent: PaymentCompletedEvent) {
        if (paymentEvent.amount > BigDecimal("1000000")) {
            throw RuntimeException("대금액 결제는 별도 승인이 필요합니다")
        }
        logger.info("신용카드 결제 처리: ${paymentEvent.orderId}")
    }
    
    private fun processBankTransferPayment(paymentEvent: PaymentCompletedEvent) {
        logger.info("계좌이체 결제 처리: ${paymentEvent.orderId}")
    }
}
