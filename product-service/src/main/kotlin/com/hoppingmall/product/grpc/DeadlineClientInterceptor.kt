package com.hoppingmall.product.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.MethodDescriptor
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor
import java.util.concurrent.TimeUnit

@GrpcGlobalClientInterceptor
class DeadlineClientInterceptor : ClientInterceptor {

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        val options = if (callOptions.deadline == null) {
            callOptions.withDeadlineAfter(5000, TimeUnit.MILLISECONDS)
        } else {
            callOptions
        }
        return next.newCall(method, options)
    }
}
