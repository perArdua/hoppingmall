package com.hoppingmall.payment.coupon.infrastructure

import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class CouponStockRedisRepository(
    private val redissonClient: RedissonClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun tryReserve(couponId: Long, userId: Long): CouponReserveResult {
        val script = redissonClient.getScript(StringCodec.INSTANCE)
        val result = script.eval<Long>(
            RScript.Mode.READ_WRITE,
            RESERVE_SCRIPT,
            RScript.ReturnType.LONG,
            listOf(stockKey(couponId), issuedKey(couponId), reservedAtKey(couponId)),
            userId.toString()
        )
        return when (result.toInt()) {
            1 -> CouponReserveResult.Success
            0 -> CouponReserveResult.Exhausted
            -1 -> CouponReserveResult.NotInitialized
            -2 -> CouponReserveResult.AlreadyIssued
            else -> CouponReserveResult.Exhausted
        }
    }

    fun initializeStock(couponId: Long, remaining: Int): Boolean {
        val bucket = redissonClient.getBucket<String>(stockKey(couponId), StringCodec.INSTANCE)
        return bucket.setIfAbsent(remaining.toString())
    }

    fun restoreStock(couponId: Long, userId: Long) {
        val script = redissonClient.getScript(StringCodec.INSTANCE)
        script.eval<Long>(
            RScript.Mode.READ_WRITE,
            RESTORE_SCRIPT,
            RScript.ReturnType.LONG,
            listOf(stockKey(couponId), issuedKey(couponId)),
            userId.toString()
        )
    }

    fun restoreStockIdempotent(couponId: Long, userId: Long): CouponRestoreResult {
        val script = redissonClient.getScript(StringCodec.INSTANCE)
        val result = script.eval<Long>(
            RScript.Mode.READ_WRITE,
            RESTORE_IDEMPOTENT_SCRIPT,
            RScript.ReturnType.LONG,
            listOf(stockKey(couponId), issuedKey(couponId), restoredKey(couponId, userId)),
            userId.toString(),
            RESTORED_TTL_SECONDS.toString()
        )
        return when (result.toInt()) {
            2 -> CouponRestoreResult.AlreadyRestored
            3 -> CouponRestoreResult.NotReserved
            else -> CouponRestoreResult.Restored
        }
    }

    fun reservationClockMillis(couponId: Long): Long {
        val script = redissonClient.getScript(StringCodec.INSTANCE)
        return script.eval<Long>(
            RScript.Mode.READ_WRITE,
            RESERVATION_CLOCK_SCRIPT,
            RScript.ReturnType.LONG,
            listOf(reservedAtKey(couponId))
        )
    }

    fun findReservedAfter(couponId: Long, userIds: Collection<Long>, reservedAfterMillis: Long): Set<Long> {
        if (userIds.isEmpty()) return emptySet()
        val script = redissonClient.getScript(StringCodec.INSTANCE)
        val recent = script.eval<List<Any>>(
            RScript.Mode.READ_WRITE,
            RESERVED_AFTER_SCRIPT,
            RScript.ReturnType.LIST,
            listOf(reservedAtKey(couponId)),
            reservedAfterMillis.toString(),
            *userIds.map { it.toString() }.toTypedArray()
        )
        return recent.mapNotNullTo(mutableSetOf()) { it.toString().toLongOrNull() }
    }

    fun deleteStock(couponId: Long) {
        redissonClient.keys.delete(stockKey(couponId), issuedKey(couponId))
    }

    fun getIssuedUserIds(couponId: Long): Set<Long> {
        val set = redissonClient.getSet<String>(issuedKey(couponId), StringCodec.INSTANCE)
        return set.readAll().mapNotNullTo(mutableSetOf()) { it.toLongOrNull() }
    }

    private fun stockKey(couponId: Long) = "coupon:{$couponId}:stock"
    private fun issuedKey(couponId: Long) = "coupon:{$couponId}:issued"
    private fun restoredKey(couponId: Long, userId: Long) = "coupon:{$couponId}:restored:$userId"
    private fun reservedAtKey(couponId: Long) = "coupon:{$couponId}:reserved-at"

    companion object {
        const val RESTORED_TTL_SECONDS = 86_400L

        const val RESERVE_SCRIPT = """
            local stockKey = KEYS[1]
            local userSetKey = KEYS[2]
            local userId = ARGV[1]

            if redis.call('EXISTS', stockKey) == 0 then
                return -1
            end

            if redis.call('SISMEMBER', userSetKey, userId) == 1 then
                return -2
            end

            local remaining = tonumber(redis.call('GET', stockKey))
            if remaining <= 0 then
                return 0
            end

            redis.call('DECR', stockKey)
            redis.call('SADD', userSetKey, userId)
            local now = redis.call('TIME')
            redis.call('HSET', KEYS[3], userId, tostring(now[1] * 1000 + math.floor(now[2] / 1000)))
            return 1
        """

        const val RESERVATION_CLOCK_SCRIPT = """
            local now = redis.call('TIME')
            return now[1] * 1000 + math.floor(now[2] / 1000)
        """

        const val RESERVED_AFTER_SCRIPT = """
            local reservedAtKey = KEYS[1]
            local reservedAfter = tonumber(ARGV[1])
            local now = redis.call('TIME')
            local nowMillis = tostring(now[1] * 1000 + math.floor(now[2] / 1000))
            local recent = {}

            for i = 2, #ARGV do
                local reservedAt = redis.call('HGET', reservedAtKey, ARGV[i])
                if not reservedAt then
                    redis.call('HSET', reservedAtKey, ARGV[i], nowMillis)
                    table.insert(recent, ARGV[i])
                elseif tonumber(reservedAt) > reservedAfter then
                    table.insert(recent, ARGV[i])
                end
            end
            return recent
        """

        const val RESTORE_SCRIPT = """
            local stockKey = KEYS[1]
            local userSetKey = KEYS[2]
            local userId = ARGV[1]

            if redis.call('SREM', userSetKey, userId) == 0 then
                return 0
            end
            if redis.call('EXISTS', stockKey) == 1 then
                redis.call('INCR', stockKey)
            end
            return 1
        """

        const val RESTORE_IDEMPOTENT_SCRIPT = """
            local stockKey = KEYS[1]
            local userSetKey = KEYS[2]
            local restoredKey = KEYS[3]
            local userId = ARGV[1]
            local ttl = tonumber(ARGV[2])

            if redis.call('EXISTS', restoredKey) == 1 then
                return 2
            end

            local removed = redis.call('SREM', userSetKey, userId)
            if removed == 1 and redis.call('EXISTS', stockKey) == 1 then
                redis.call('INCR', stockKey)
            end
            redis.call('SET', restoredKey, '1', 'EX', ttl)
            if removed == 0 then
                return 3
            end
            return 1
        """
    }
}
