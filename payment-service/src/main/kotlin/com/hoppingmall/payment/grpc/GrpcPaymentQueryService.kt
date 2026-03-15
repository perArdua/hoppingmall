package com.hoppingmall.payment.grpc

import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import io.grpc.Status
import io.grpc.StatusException
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class GrpcPaymentQueryService(
    private val paymentRepository: PaymentRepository
) : PaymentQueryServiceGrpcKt.PaymentQueryServiceCoroutineImplBase() {

    override suspend fun findPaymentByOrderId(request: PaymentOrderIdRequest): PaymentResponse {
        val payment = paymentRepository.findByOrderId(request.orderId)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Payment not found for orderId=${request.orderId}"))
        return paymentResponse {
            id = payment.id!!
            orderId = payment.orderId
            amount = payment.amount.toPlainString()
            pointAmount = payment.pointAmount.toPlainString()
            payment.couponId?.let { couponId = it }
            status = payment.status.name
        }
    }

    override suspend fun findPaymentById(request: PaymentIdRequest): PaymentResponse {
        val payment = paymentRepository.findById(request.paymentId).orElse(null)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Payment not found for id=${request.paymentId}"))
        return paymentResponse {
            id = payment.id!!
            orderId = payment.orderId
            amount = payment.amount.toPlainString()
            pointAmount = payment.pointAmount.toPlainString()
            payment.couponId?.let { couponId = it }
            status = payment.status.name
        }
    }
}
