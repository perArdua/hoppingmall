package com.hoppingmall.common

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(CallNotPermittedException::class)
class CircuitBreakerExceptionHandler(
    private val circuitBreakerRegistry: ObjectProvider<CircuitBreakerRegistry>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CallNotPermittedException::class)
    fun handleCallNotPermitted(ex: CallNotPermittedException): ResponseEntity<ApiResponse<Unit>> {
        val retryAfterSeconds = retryAfterSeconds(ex.causingCircuitBreakerName)
        log.warn("CallNotPermitted: circuitBreaker={}, retryAfter={}s", ex.causingCircuitBreakerName, retryAfterSeconds)
        return ResponseEntity.status(CommonErrorCode.DEPENDENCY_UNAVAILABLE.status)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(ApiResponse.failure(CommonErrorCode.DEPENDENCY_UNAVAILABLE))
    }

    private fun retryAfterSeconds(circuitBreakerName: String): Long {
        val waitMillis = circuitBreakerRegistry.ifAvailable
            ?.find(circuitBreakerName)
            ?.orElse(null)
            ?.circuitBreakerConfig
            ?.waitIntervalFunctionInOpenState
            ?.apply(1)
            ?: return DEFAULT_RETRY_AFTER_SECONDS
        return ((waitMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtLeast(1)
    }

    private companion object {
        const val DEFAULT_RETRY_AFTER_SECONDS = 10L
        const val MILLIS_PER_SECOND = 1000L
    }
}
