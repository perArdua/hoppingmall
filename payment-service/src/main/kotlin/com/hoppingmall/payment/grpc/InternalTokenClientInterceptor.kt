package com.hoppingmall.payment.grpc

import io.grpc.*
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor
import org.springframework.beans.factory.annotation.Value

@GrpcGlobalClientInterceptor
class InternalTokenClientInterceptor(
    @Value("\${internal.service.token}") private val token: String
) : ClientInterceptor {

    companion object {
        val INTERNAL_TOKEN_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                headers.put(INTERNAL_TOKEN_KEY, token)
                super.start(responseListener, headers)
            }
        }
    }
}
