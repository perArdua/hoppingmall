package com.hoppingmall.mall.membership.grpc

import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.enum.MembershipGrade
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class GrpcMembershipQueryService(
    private val membershipRepository: MembershipRepository
) : MembershipQueryServiceGrpcKt.MembershipQueryServiceCoroutineImplBase() {

    override suspend fun getPointEarningRate(request: UserIdRequest): EarningRateResponse {
        val membership = membershipRepository.findByUserId(request.userId)
        val rate = membership?.grade?.pointEarningRate ?: MembershipGrade.BRONZE.pointEarningRate
        return earningRateResponse { earningRate = rate.toPlainString() }
    }
}
