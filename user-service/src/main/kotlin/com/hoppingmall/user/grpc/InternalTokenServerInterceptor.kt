package com.hoppingmall.user.grpc

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.beans.factory.annotation.Value

@GrpcGlobalServerInterceptor
class InternalTokenServerInterceptor(
    @Value("\${internal.service.token}") private val expectedToken: String
) : ServerInterceptor {

    companion object {
        val INTERNAL_TOKEN_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val clientToken = headers.get(INTERNAL_TOKEN_KEY)
        if (clientToken != expectedToken) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid internal token"), Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        return next.startCall(call, headers)
    }
}
