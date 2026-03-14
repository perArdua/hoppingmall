package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.payment.api.PaymentInfo
import com.hoppingmall.mall.payment.api.PaymentQueryPort
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class PaymentQueryPortAdapter(
    private val paymentRepository: PaymentRepository
) : PaymentQueryPort {

    override fun findById(paymentId: Long): PaymentInfo? {
        return paymentRepository.findByIdOrNull(paymentId)?.let {
            PaymentInfo(
                id = it.id!!,
                orderId = it.orderId,
                amount = it.amount,
                pointAmount = it.pointAmount,
                couponId = it.couponId,
                status = it.status.name,
                isSuccess = it.isSuccess()
            )
        }
    }

    override fun findByOrderId(orderId: Long): PaymentInfo? {
        return paymentRepository.findByOrderId(orderId)?.let {
            PaymentInfo(
                id = it.id!!,
                orderId = it.orderId,
                amount = it.amount,
                pointAmount = it.pointAmount,
                couponId = it.couponId,
                status = it.status.name,
                isSuccess = it.isSuccess()
            )
        }
    }
}
