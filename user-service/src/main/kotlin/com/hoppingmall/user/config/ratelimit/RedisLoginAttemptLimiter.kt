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
    private val clock: Clock
) : LoginAttemptLimiter {

    override fun remainingBlock(email: String): Duration {
        val blockedUntil = redissonClient.getBucket<String>(blockedKey(email), StringCodec.INSTANCE).get()
            ?: return Duration.ZERO
        val remainingMillis = blockedUntil.toLong() - clock.millis()
        return if (remainingMillis > 0) Duration.ofMillis(remainingMillis) else Duration.ZERO
    }

    override fun recordFailure(email: String) {
        val failuresKey = failuresKey(email)
        val now = clock.millis()
        redissonClient.getScript(StringCodec.INSTANCE).eval<Long>(
            failuresKey,
            RScript.Mode.READ_WRITE,
            RECORD_FAILURE_SCRIPT,
            RScript.ReturnType.LONG,
            listOf<Any>(failuresKey, blockedKey(email)),
            now.toString(),
            WINDOW.toMillis().toString(),
            MAX_FAILURES.toString(),
            BLOCK_DURATION.toMillis().toString(),
            (now + BLOCK_DURATION.toMillis()).toString(),
            UUID.randomUUID().toString()
        )
    }

    override fun reset(email: String) {
        redissonClient.keys.delete(failuresKey(email), blockedKey(email))
    }

    private fun failuresKey(email: String): String = "$KEY_PREFIX{${emailHash(email)}}:failures"

    private fun blockedKey(email: String): String = "$KEY_PREFIX{${emailHash(email)}}:blocked"

    private fun emailHash(email: String): String {
        val normalized = email.trim().lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    companion object {
        private const val MAX_FAILURES = 10
        private val WINDOW: Duration = Duration.ofMinutes(15)
        private val BLOCK_DURATION: Duration = Duration.ofMinutes(15)

        private const val KEY_PREFIX = "login-attempt:"

        private val RECORD_FAILURE_SCRIPT = """
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local blockedUntil = tonumber(redis.call('GET', KEYS[2]) or '0')
            if now < blockedUntil then
                return 0
            end
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            redis.call('ZADD', KEYS[1], now, ARGV[6])
            local count = redis.call('ZCARD', KEYS[1])
            if count >= tonumber(ARGV[3]) then
                redis.call('SET', KEYS[2], ARGV[5], 'PX', ARGV[4])
                redis.call('DEL', KEYS[1])
            else
                redis.call('PEXPIRE', KEYS[1], window)
            end
            return count
        """.trimIndent()
    }
}
