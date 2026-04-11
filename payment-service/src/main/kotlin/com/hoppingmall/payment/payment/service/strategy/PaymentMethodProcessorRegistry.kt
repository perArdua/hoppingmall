package com.hoppingmall.payment.payment.service.strategy

import com.hoppingmall.payment.payment.enum.PaymentMethod
import org.springframework.stereotype.Component

@Component
class PaymentMethodProcessorRegistry(
    processors: List<PaymentMethodProcessor>
) {
    private val processorMap: Map<PaymentMethod, PaymentMethodProcessor> =
        processors.associateBy { it.method }

    fun getProcessor(method: PaymentMethod): PaymentMethodProcessor =
        processorMap[method]
            ?: throw IllegalArgumentException("지원하지 않는 결제 수단: $method")
}
