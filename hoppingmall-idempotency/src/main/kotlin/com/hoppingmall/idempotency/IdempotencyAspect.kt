package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.hoppingmall.common.config.GatewayHeaderAuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.TreeMap
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

    private val replayReader: ObjectReader = objectMapper.readerFor(Any::class.java)
        .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

    @Value("\${idempotency.lock-timeout-seconds:0}")
    private var lockTimeoutOverrideSeconds: Long = 0

    @Value("\${idempotency.save-in-tx:true}")
    private var saveInTx: Boolean = true

    @Value("\${idempotency.claim-by-commit:false}")
    private var claimByCommit: Boolean = false

    @Around("@annotation(idempotent)")
    fun handleIdempotency(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            ?: return joinPoint.proceed()

        val request = requestAttributes.request
        val key = request.getHeader(IDEMPOTENCY_KEY_HEADER)
            ?: throw IdempotencyKeyMissingException()
        val scopedKey = scopedKey(request, key)
        val fingerprint = requestFingerprint(joinPoint, request)

        val existing = idempotencyService.findByKey(scopedKey)
        if (existing != null) {
            log.info("멱등성 캐시 히트: key={}, endpoint={}", key, existing.endpoint)
            return buildCachedResponse(existing, fingerprint)
        }

        if (claimByCommit && idempotent.claimByCommit) {
            return proceedClaimingByCommit(joinPoint, idempotent, request, key, scopedKey, fingerprint)
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
                return buildCachedResponse(existingAfterLock, fingerprint)
            }

            val pending = if (saveInTx) {
                PendingIdempotency(scopedKey, request.method, request.requestURI, fingerprint, idempotent.ttlHours)
                    .also { IdempotencyRequestContext.open(it) }
            } else {
                null
            }
            val result = try {
                joinPoint.proceed()
            } finally {
                if (pending != null) IdempotencyRequestContext.clear()
            }

            if (pending?.savedInTransaction == true) {
                log.info("멱등성 레코드 트랜잭션 내 저장: key={}, endpoint={}", key, request.requestURI)
                return result
            }

            saveIfSuccessful(result, request, key, scopedKey, fingerprint, idempotent)
            return result
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    private fun proceedClaimingByCommit(
        joinPoint: ProceedingJoinPoint,
        idempotent: Idempotent,
        request: HttpServletRequest,
        key: String,
        scopedKey: String,
        fingerprint: String
    ): Any? {
        val pending = PendingIdempotency(scopedKey, request.method, request.requestURI, fingerprint, idempotent.ttlHours, claimByCommit = true)
        IdempotencyRequestContext.open(pending)
        val result = try {
            joinPoint.proceed()
        } catch (e: RuntimeException) {
            val winner = idempotencyService.findByKey(scopedKey) ?: throw e
            log.info("멱등성 커밋 선점 경합, 먼저 커밋된 응답 반환: key={}, cause={}", key, e.javaClass.simpleName)
            return buildCachedResponse(winner, fingerprint)
        } finally {
            IdempotencyRequestContext.clear()
        }
        if (!pending.savedInTransaction) {
            saveIfSuccessful(result, request, key, scopedKey, fingerprint, idempotent)
        }
        return result
    }

    private fun saveIfSuccessful(
        result: Any?,
        request: HttpServletRequest,
        key: String,
        scopedKey: String,
        fingerprint: String,
        idempotent: Idempotent
    ) {
        if (result !is ResponseEntity<*> || !result.statusCode.is2xxSuccessful) return
        idempotencyService.save(
            key = scopedKey,
            httpMethod = request.method,
            endpoint = request.requestURI,
            responseStatus = result.statusCode.value(),
            responseBody = objectMapper.writeValueAsString(result.body),
            requestFingerprint = fingerprint,
            ttlHours = idempotent.ttlHours
        )
        log.info("멱등성 레코드 저장: key={}, endpoint={}", key, request.requestURI)
    }

    private fun scopedKey(request: HttpServletRequest, key: String): String {
        val requester = request.getHeader(GatewayHeaderAuthenticationFilter.USER_ID_HEADER)
            ?.trim()
            ?.toLongOrNull()
            ?.toString()
            ?: ANONYMOUS_REQUESTER
        return sha256(listOf(requester, request.method, request.requestURI, key).joinToString(SCOPE_DELIMITER))
    }

    private fun requestFingerprint(joinPoint: ProceedingJoinPoint, request: HttpServletRequest): String {
        val method = (joinPoint.signature as? MethodSignature)?.method
        val args = joinPoint.args.orEmpty()
        val bodies = method?.parameters.orEmpty()
            .mapIndexedNotNull { index, parameter ->
                if (parameter.isAnnotationPresent(RequestBody::class.java)) canonical(objectMapper.valueToTree(args.getOrNull(index))) else null
            }
        val query = TreeMap(request.parameterMap.orEmpty().mapValues { it.value.toList() })
        val payload = objectMapper.createObjectNode()
        payload.set<JsonNode>("body", objectMapper.valueToTree(bodies))
        payload.set<JsonNode>("query", objectMapper.valueToTree(query))
        return sha256(objectMapper.writeValueAsString(payload))
    }

    private fun canonical(node: JsonNode?): JsonNode? = when {
        node == null -> null
        node.isObject -> objectMapper.createObjectNode().also { sorted ->
            node.fieldNames().asSequence().sorted().forEach { name -> sorted.set<JsonNode>(name, canonical(node.get(name))) }
        }
        node.isArray -> objectMapper.createArrayNode().also { array -> node.forEach { array.add(canonical(it)) } }
        else -> node
    }

    private fun sha256(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance(SCOPE_HASH_ALGORITHM).digest(value.toByteArray(StandardCharsets.UTF_8)))

    private fun buildCachedResponse(record: IdempotencyRecord, fingerprint: String): ResponseEntity<Any> {
        if (record.requestFingerprint != null && record.requestFingerprint != fingerprint) {
            log.warn("멱등성 키 재사용 거절: endpoint={}", record.endpoint)
            throw IdempotencyKeyReusedException()
        }
        val body = replayReader.readValue<Any>(record.responseBody)
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
