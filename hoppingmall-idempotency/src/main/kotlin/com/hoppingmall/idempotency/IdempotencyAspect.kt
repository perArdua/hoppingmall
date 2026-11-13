package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.config.GatewayHeaderAuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit

@Aspect
@Component
@ConditionalOnBean(RedissonClient::class)
class IdempotencyAspect(
    private val idempotencyService: IdempotencyService,
    private val redissonClient: RedissonClient,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${idempotency.lock-timeout-seconds:0}")
    private var lockTimeoutOverrideSeconds: Long = 0

    @Around("@annotation(idempotent)")
    fun handleIdempotency(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            ?: return joinPoint.proceed()

        val request = requestAttributes.request
        val key = request.getHeader(IDEMPOTENCY_KEY_HEADER)
            ?: throw IdempotencyKeyMissingException()
        val scopedKey = scopedKey(request, key)

        val existing = idempotencyService.findByKey(scopedKey)
        if (existing != null) {
            log.info("멱등성 캐시 히트: key={}, endpoint={}", key, existing.endpoint)
            return buildCachedResponse(existing)
        }

        val lockKey = "idempotency:$scopedKey"
        val lock = redissonClient.getLock(lockKey)

        if (!lock.tryLock(0, lockTimeoutSeconds(idempotent), TimeUnit.SECONDS)) {
            throw IdempotencyConflictException()
        }

        try {
            val existingAfterLock = idempotencyService.findByKey(scopedKey)
            if (existingAfterLock != null) {
                log.info("멱등성 캐시 히트 (락 후): key={}", key)
                return buildCachedResponse(existingAfterLock)
            }

            val result = joinPoint.proceed()

            if (result is ResponseEntity<*> && result.statusCode.is2xxSuccessful) {
                idempotencyService.save(
                    key = scopedKey,
                    httpMethod = request.method,
                    endpoint = request.requestURI,
                    responseStatus = result.statusCode.value(),
                    responseBody = objectMapper.writeValueAsString(result.body),
                    ttlHours = idempotent.ttlHours
                )
                log.info("멱등성 레코드 저장: key={}, endpoint={}", key, request.requestURI)
            }

            return result
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    private fun scopedKey(request: HttpServletRequest, key: String): String {
        val requester = request.getHeader(GatewayHeaderAuthenticationFilter.USER_ID_HEADER)
            ?.trim()
            ?.toLongOrNull()
            ?.toString()
            ?: ANONYMOUS_REQUESTER
        val scope = listOf(requester, request.method, request.requestURI, key).joinToString(SCOPE_DELIMITER)
        val digest = MessageDigest.getInstance(SCOPE_HASH_ALGORITHM).digest(scope.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    private fun buildCachedResponse(record: IdempotencyRecord): ResponseEntity<Any> {
        val body = objectMapper.readValue(record.responseBody, Any::class.java)
        return ResponseEntity.status(record.responseStatus).body(body)
    }

    private fun lockTimeoutSeconds(idempotent: Idempotent): Long = when {
        lockTimeoutOverrideSeconds > 0 -> lockTimeoutOverrideSeconds
        idempotent.lockTimeoutSeconds > 0 -> idempotent.lockTimeoutSeconds
        else -> Idempotent.LOCK_HELD_UNTIL_UNLOCK
    }

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        private const val ANONYMOUS_REQUESTER = "anonymous"
        private const val SCOPE_DELIMITER = "\n"
        private const val SCOPE_HASH_ALGORITHM = "SHA-256"
    }
}
