package com.hoppingmall.order.grpc

import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.payment.grpc.PaymentIdRequest
import com.hoppingmall.payment.grpc.PaymentOrderIdRequest
import com.hoppingmall.payment.grpc.PaymentQueryServiceGrpc
import com.hoppingmall.payment.grpc.PaymentResponse
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.client.inject.GrpcClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@Profile("grpc")
class GrpcPaymentQueryAdapter(
    @GrpcClient("payment-service") private val stub: PaymentQueryServiceGrpc.PaymentQueryServiceBlockingStub
) : PaymentQueryPort {

    private val log = LoggerFactory.getLogger(GrpcPaymentQueryAdapter::class.java)

    @CircuitBreaker(name = "payment-query", fallbackMethod = "findByOrderIdFallback")
    @Retry(name = "payment-query")
    override fun findByOrderId(orderId: Long): PaymentInfo? {
        val response = stub.findPaymentByOrderId(
            PaymentOrderIdRequest.newBuilder().setOrderId(orderId).build()
        )
        return response.toPaymentInfo()
    }

    @CircuitBreaker(name = "payment-query", fallbackMethod = "findByIdFallback")
    @Retry(name = "payment-query")
    override fun findById(paymentId: Long): PaymentInfo? {
        val response = stub.findPaymentById(
            PaymentIdRequest.newBuilder().setPaymentId(paymentId).build()
        )
        return response.toPaymentInfo()
    }

    private fun findByOrderIdFallback(orderId: Long, e: Exception): PaymentInfo? {
        if (e is StatusRuntimeException && e.status.code == Status.Code.NOT_FOUND) return null
        log.warn("CB fallback: 결제 조회 실패 orderId=$orderId", e)
        return null
    }

    private fun findByIdFallback(paymentId: Long, e: Exception): PaymentInfo? {
        if (e is StatusRuntimeException && e.status.code == Status.Code.NOT_FOUND) return null
        log.warn("CB fallback: 결제 조회 실패 paymentId=$paymentId", e)
        return null
    }

    private fun PaymentResponse.toPaymentInfo() = PaymentInfo(
        id = id,
        orderId = orderId,
        amount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
        pointAmount = pointAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
        couponId = if (hasCouponId()) couponId else null,
        status = status
    )
}
