package com.hoppingmall.payment.payment.service.strategy

import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.enum.PaymentMethod
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BankTransferProcessor : PaymentMethodProcessor {

    private val logger = LoggerFactory.getLogger(BankTransferProcessor::class.java)

    override val method: PaymentMethod = PaymentMethod.BANK_TRANSFER

    override fun process(event: PaymentCompletedEvent) {
        logger.info("계좌이체 결제 처리: ${event.orderId}")
    }
}
