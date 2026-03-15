package com.hoppingmall.mall.membership.grpc

import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.slf4j.MDC

@GrpcGlobalServerInterceptor
class TracingServerInterceptor : ServerInterceptor {

    companion object {
        val TRACE_ID_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER)
        val USER_ID_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        headers.get(TRACE_ID_KEY)?.let { MDC.put("traceId", it) }
        headers.get(USER_ID_KEY)?.let { MDC.put("userId", it) }

        return object : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
            next.startCall(call, headers)
        ) {
            override fun onComplete() {
                try {
                    super.onComplete()
                } finally {
                    MDC.remove("traceId")
                    MDC.remove("userId")
                }
            }

            override fun onCancel() {
                try {
                    super.onCancel()
                } finally {
                    MDC.remove("traceId")
                    MDC.remove("userId")
                }
            }
        }
    }
}
