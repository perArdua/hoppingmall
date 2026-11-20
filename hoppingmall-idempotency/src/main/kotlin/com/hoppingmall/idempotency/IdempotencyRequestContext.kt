package com.hoppingmall.idempotency

class PendingIdempotency(
    val key: String,
    val httpMethod: String,
    val endpoint: String,
    val requestFingerprint: String,
    val ttlHours: Long,
    val claimByCommit: Boolean = false
) {
    var savedInTransaction: Boolean = false
        internal set
}

object IdempotencyRequestContext {

    private val holder = ThreadLocal<PendingIdempotency>()

    fun open(pending: PendingIdempotency) = holder.set(pending)

    fun current(): PendingIdempotency? = holder.get()

    fun claimKey(): String? = holder.get()?.takeIf { it.claimByCommit }?.key

    fun clear() = holder.remove()
}
