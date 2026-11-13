package com.hoppingmall.idempotency

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    val ttlHours: Long = 24,
    val lockTimeoutSeconds: Long = Idempotent.LOCK_HELD_UNTIL_UNLOCK
) {
    companion object {
        const val LOCK_HELD_UNTIL_UNLOCK = -1L
    }
}
