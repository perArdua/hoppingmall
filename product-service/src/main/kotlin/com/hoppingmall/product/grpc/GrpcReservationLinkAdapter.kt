package com.hoppingmall.product.grpc

import com.hoppingmall.product.inventory.service.ReservationLinkPort
import com.hoppingmall.reservation.grpc.ReservationLinkRequest
import com.hoppingmall.reservation.grpc.ReservationLinkServiceGrpc
import net.devh.boot.grpc.client.inject.GrpcClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["inventory.link-check.enabled"], havingValue = "true")
class GrpcReservationLinkAdapter(
    @GrpcClient("order-service", interceptors = [ConnectBackoffResetClientInterceptor::class]) private val stub: ReservationLinkServiceGrpc.ReservationLinkServiceBlockingStub,
    @Value("\${inventory.link-check.deadline-ms:2000}") private val deadlineMs: Long
) : ReservationLinkPort {

    override fun check(reservationIds: List<String>): ReservationLinkPort.LinkResult {
        val response = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .checkReservationLinks(ReservationLinkRequest.newBuilder().addAllReservationIds(reservationIds).build())
        return ReservationLinkPort.LinkResult(response.linkedList.toList(), response.abortedList.toList())
    }
}
