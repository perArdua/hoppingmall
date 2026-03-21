package com.hoppingmall.payment.payment.service.strategy

import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.enum.PaymentMethod
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class CreditCardProcessor : PaymentMethodProcessor {

    private val logger = LoggerFactory.getLogger(CreditCardProcessor::class.java)

    override val method: PaymentMethod = PaymentMethod.CREDIT_CARD

    override fun process(event: PaymentCompletedEvent) {
        if (event.amount > BigDecimal("1000000")) {
            throw RuntimeException("대금액 결제는 별도 승인이 필요합니다")
        }
        logger.info("신용카드 결제 처리: ${event.orderId}")
    }
}
