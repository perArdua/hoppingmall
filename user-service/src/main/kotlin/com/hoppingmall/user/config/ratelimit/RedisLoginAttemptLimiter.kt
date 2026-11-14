package com.hoppingmall.user.config.ratelimit

import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

class RedisLoginAttemptLimiter(
    private val redissonClient: RedissonClient,
    private val clock: Clock,
    private val properties: LoginAttemptLimitProperties = LoginAttemptLimitProperties()
) : LoginAttemptLimiter {

    override fun remainingBlock(email: String, clientIp: String): Duration {
        val now = clock.millis()
        val blockedUntil = listOf(pairBlockedKey(email, clientIp), emailBlockedKey(email))
            .mapNotNull { redissonClient.getBucket<String>(it, StringCodec.INSTANCE).get()?.toLong() }
            .maxOrNull()
            ?: return Duration.ZERO
        val remainingMillis = blockedUntil - now
        return if (remainingMillis > 0) Duration.ofMillis(remainingMillis) else Duration.ZERO
    }

    override fun recordFailure(email: String, clientIp: String) {
        val now = clock.millis()
        val blockMillis = properties.blockDuration.toMillis()
        redissonClient.getScript(StringCodec.INSTANCE).eval<Long>(
            emailFailuresKey(email),
            RScript.Mode.READ_WRITE,
            RECORD_FAILURE_SCRIPT,
            RScript.ReturnType.LONG,
            listOf<Any>(
                pairFailuresKey(email, clientIp),
                pairBlockedKey(email, clientIp),
                emailFailuresKey(email),
                emailBlockedKey(email)
            ),
            now.toString(),
            properties.window.toMillis().toString(),
            properties.maxFailuresPerEmailAndIp.toString(),
            properties.maxFailuresPerEmail.toString(),
            blockMillis.toString(),
            (now + blockMillis).toString(),
            UUID.randomUUID().toString()
        )
    }

    override fun reset(email: String, clientIp: String) {
        redissonClient.keys.delete(
            pairFailuresKey(email, clientIp),
            pairBlockedKey(email, clientIp),
            emailFailuresKey(email),
            emailBlockedKey(email)
        )
    }

    private fun emailFailuresKey(email: String): String = "${emailPrefix(email)}:failures"

    private fun emailBlockedKey(email: String): String = "${emailPrefix(email)}:blocked"

    private fun pairFailuresKey(email: String, clientIp: String): String = "${emailPrefix(email)}:ip:${sha256(clientIp.trim())}:failures"

    private fun pairBlockedKey(email: String, clientIp: String): String = "${emailPrefix(email)}:ip:${sha256(clientIp.trim())}:blocked"

    private fun emailPrefix(email: String): String = "$KEY_PREFIX{${sha256(email.trim().lowercase(Locale.ROOT))}}"

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    companion object {
        private const val KEY_PREFIX = "login-attempt:"

        private val RECORD_FAILURE_SCRIPT = """
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            if now < tonumber(redis.call('GET', KEYS[2]) or '0') or now < tonumber(redis.call('GET', KEYS[4]) or '0') then
                return 0
            end
            local function record(failuresKey, blockedKey, maxFailures)
                redis.call('ZREMRANGEBYSCORE', failuresKey, '-inf', now - window)
                redis.call('ZADD', failuresKey, now, ARGV[7])
                if redis.call('ZCARD', failuresKey) >= maxFailures then
                    redis.call('SET', blockedKey, ARGV[6], 'PX', ARGV[5])
                    redis.call('DEL', failuresKey)
                else
                    redis.call('PEXPIRE', failuresKey, window)
                end
            end
            record(KEYS[1], KEYS[2], tonumber(ARGV[3]))
            record(KEYS[3], KEYS[4], tonumber(ARGV[4]))
            return 1
        """.trimIndent()
    }
}
