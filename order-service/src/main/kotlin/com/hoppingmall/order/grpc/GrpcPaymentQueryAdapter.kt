package com.hoppingmall.order.grpc

import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.payment.grpc.PaymentIdRequest
import com.hoppingmall.payment.grpc.PaymentOrderIdRequest
import com.hoppingmall.payment.grpc.PaymentQueryServiceGrpc
import com.hoppingmall.payment.grpc.PaymentResponse
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

    override fun findByOrderId(orderId: Long): PaymentInfo? {
        return try {
            val response = stub.findPaymentByOrderId(
                PaymentOrderIdRequest.newBuilder().setOrderId(orderId).build()
            )
            response.toPaymentInfo()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) null
            else {
                log.warn("결제 조회 실패: orderId=$orderId", e)
                null
            }
        }
    }

    override fun findById(paymentId: Long): PaymentInfo? {
        return try {
            val response = stub.findPaymentById(
                PaymentIdRequest.newBuilder().setPaymentId(paymentId).build()
            )
            response.toPaymentInfo()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) null
            else {
                log.warn("결제 조회 실패: paymentId=$paymentId", e)
                null
            }
        }
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
