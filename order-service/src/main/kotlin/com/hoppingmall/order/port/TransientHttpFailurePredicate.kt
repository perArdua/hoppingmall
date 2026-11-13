package com.hoppingmall.order.port

import org.springframework.web.client.ResourceAccessException
import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.function.Predicate

class TransientHttpFailurePredicate : Predicate<Throwable> {

    override fun test(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause.takeIf { cause -> cause !== it } }
            .take(MAX_CAUSE_DEPTH)
            .any(::isTransient)

    private fun isTransient(throwable: Throwable): Boolean =
        throwable is ResourceAccessException || throwable is IOException || throwable is TimeoutException

    companion object {
        private const val MAX_CAUSE_DEPTH = 5
    }
}
