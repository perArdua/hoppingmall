package com.hoppingmall.order.grpc

import com.hoppingmall.order.order.service.ReservationFence
import com.hoppingmall.reservation.grpc.ReservationLinkRequest
import com.hoppingmall.reservation.grpc.ReservationLinkResponse
import com.hoppingmall.reservation.grpc.ReservationLinkServiceGrpcKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.devh.boot.grpc.server.service.GrpcService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@GrpcService
@ConditionalOnProperty(name = ["order.reservation.fence.enabled"], havingValue = "true")
class GrpcReservationLinkService(
    private val fence: ReservationFence
) : ReservationLinkServiceGrpcKt.ReservationLinkServiceCoroutineImplBase() {

    override suspend fun checkReservationLinks(request: ReservationLinkRequest): ReservationLinkResponse {
        val linked = mutableListOf<String>()
        val aborted = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            request.reservationIdsList.distinct().forEach { rid ->
                if (fence.decide(rid) == ReservationFence.LINKED) linked.add(rid) else aborted.add(rid)
            }
        }
        return ReservationLinkResponse.newBuilder().addAllLinked(linked).addAllAborted(aborted).build()
    }
}
