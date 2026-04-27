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
            listOf(stockKey(couponId), issuedKey(couponId)),
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

    fun deleteStock(couponId: Long) {
        redissonClient.keys.delete(stockKey(couponId), issuedKey(couponId))
    }

    private fun stockKey(couponId: Long) = "coupon:{$couponId}:stock"
    private fun issuedKey(couponId: Long) = "coupon:{$couponId}:issued"

    companion object {
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
            return 1
        """

        const val RESTORE_SCRIPT = """
            local stockKey = KEYS[1]
            local userSetKey = KEYS[2]
            local userId = ARGV[1]

            if redis.call('EXISTS', stockKey) == 1 then
                redis.call('INCR', stockKey)
            end
            redis.call('SREM', userSetKey, userId)
            return 1
        """
    }
}
