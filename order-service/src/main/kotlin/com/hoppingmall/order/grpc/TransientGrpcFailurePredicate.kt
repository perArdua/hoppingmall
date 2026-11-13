package com.hoppingmall.order.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.io.IOException
import java.util.function.Predicate

class TransientGrpcFailurePredicate : Predicate<Throwable> {

    override fun test(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause.takeIf { cause -> cause !== it } }
            .take(MAX_CAUSE_DEPTH)
            .any(::isTransient)

    private fun isTransient(throwable: Throwable): Boolean = when (throwable) {
        is StatusRuntimeException -> throwable.status.code in TRANSIENT_CODES
        is IOException -> true
        else -> false
    }

    companion object {
        private const val MAX_CAUSE_DEPTH = 5
        private val TRANSIENT_CODES = setOf(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED)
    }
}
