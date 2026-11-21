package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class IdempotencyTxRecorder(
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper
) {

    fun recordIfPending(response: () -> ResponseEntity<*>) {
        val pending = IdempotencyRequestContext.current() ?: return
        val result = response()
        if (!result.statusCode.is2xxSuccessful) return
        idempotencyService.saveInCurrentTransaction(
            key = pending.key,
            httpMethod = pending.httpMethod,
            endpoint = pending.endpoint,
            responseStatus = result.statusCode.value(),
            responseBody = objectMapper.writeValueAsString(result.body),
            requestFingerprint = pending.requestFingerprint,
            ttlHours = pending.ttlHours
        )
        pending.savedInTransaction = true
    }

    fun claimedByCommittedRequest(): Boolean {
        val key = IdempotencyRequestContext.claimKey() ?: return false
        return idempotencyService.findByKey(key) != null
    }
}
