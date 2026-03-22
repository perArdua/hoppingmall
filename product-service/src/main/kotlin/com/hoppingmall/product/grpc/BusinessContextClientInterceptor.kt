package com.hoppingmall.product.grpc

import io.grpc.*
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor
import org.slf4j.MDC

@GrpcGlobalClientInterceptor
class BusinessContextClientInterceptor : ClientInterceptor {

    companion object {
        val USER_ID_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER)
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
                MDC.get("userId")?.let { headers.put(USER_ID_KEY, it) }
                super.start(responseListener, headers)
            }
        }
    }
}
