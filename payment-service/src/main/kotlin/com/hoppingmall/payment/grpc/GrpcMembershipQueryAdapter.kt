package com.hoppingmall.payment.grpc

import com.hoppingmall.mall.membership.grpc.MembershipQueryServiceGrpc
import com.hoppingmall.mall.membership.grpc.UserIdRequest
import com.hoppingmall.payment.port.MembershipQueryPort
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.client.inject.GrpcClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@Profile("grpc")
class GrpcMembershipQueryAdapter(
    @GrpcClient("monolith") private val stub: MembershipQueryServiceGrpc.MembershipQueryServiceBlockingStub
) : MembershipQueryPort {

    private val log = LoggerFactory.getLogger(GrpcMembershipQueryAdapter::class.java)

    override fun getPointEarningRate(userId: Long): BigDecimal {
        return try {
            val response = stub.getPointEarningRate(
                UserIdRequest.newBuilder().setUserId(userId).build()
            )
            response.earningRate.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: StatusRuntimeException) {
            log.warn("멤버십 적립률 조회 실패: userId=$userId", e)
            BigDecimal("0.01")
        }
    }
}
