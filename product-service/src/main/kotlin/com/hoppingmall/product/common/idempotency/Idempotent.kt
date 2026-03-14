package com.hoppingmall.product.common.idempotency

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    val ttlHours: Long = 24,
    val lockTimeoutSeconds: Long = 10
)
