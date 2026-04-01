package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
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

    @Around("@annotation(idempotent)")
    fun handleIdempotency(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            ?: return joinPoint.proceed()

        val request = requestAttributes.request
        val key = request.getHeader(IDEMPOTENCY_KEY_HEADER)
            ?: throw IdempotencyKeyMissingException()

        val existing = idempotencyService.findByKey(key)
        if (existing != null) {
            log.info("멱등성 캐시 히트: key={}, endpoint={}", key, existing.endpoint)
            return buildCachedResponse(existing)
        }

        val lockKey = "idempotency:$key"
        val lock = redissonClient.getLock(lockKey)

        if (!lock.tryLock(0, idempotent.lockTimeoutSeconds, TimeUnit.SECONDS)) {
            throw IdempotencyConflictException()
        }

        try {
            val existingAfterLock = idempotencyService.findByKey(key)
            if (existingAfterLock != null) {
                log.info("멱등성 캐시 히트 (락 후): key={}", key)
                return buildCachedResponse(existingAfterLock)
            }

            val result = joinPoint.proceed()

            if (result is ResponseEntity<*> && result.statusCode.is2xxSuccessful) {
                idempotencyService.save(
                    key = key,
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

    private fun buildCachedResponse(record: IdempotencyRecord): ResponseEntity<Any> {
        val body = objectMapper.readValue(record.responseBody, Any::class.java)
        return ResponseEntity.status(record.responseStatus).body(body)
    }

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}
