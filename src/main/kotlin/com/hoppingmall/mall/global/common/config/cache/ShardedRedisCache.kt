package com.hoppingmall.mall.global.common.config.cache

import org.springframework.cache.Cache
import java.util.concurrent.Callable
import java.util.concurrent.ThreadLocalRandom

class ShardedRedisCache(
    private val delegate: Cache,
    private val shardCount: Int
) : Cache {

    override fun getName(): String = delegate.name

    override fun getNativeCache(): Any = delegate.nativeCache

    override fun get(key: Any): Cache.ValueWrapper? {
        val shardIndex = ThreadLocalRandom.current().nextInt(shardCount)
        val shardValue = delegate.get(shardKey(key, shardIndex))
        if (shardValue != null) return shardValue

        return delegate.get(key)
    }

    override fun <T : Any?> get(key: Any, type: Class<T>?): T? {
        val shardIndex = ThreadLocalRandom.current().nextInt(shardCount)
        val shardValue = delegate.get(shardKey(key, shardIndex), type)
        if (shardValue != null) return shardValue

        return delegate.get(key, type)
    }

    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? {
        return delegate.get(key, valueLoader)
    }

    override fun put(key: Any, value: Any?) {
        delegate.put(key, value)
        repeat(shardCount) { i ->
            delegate.put(shardKey(key, i), value)
        }
    }

    override fun evict(key: Any) {
        delegate.evict(key)
        repeat(shardCount) { i ->
            delegate.evict(shardKey(key, i))
        }
    }

    override fun clear() {
        delegate.clear()
    }

    private fun shardKey(key: Any, index: Int): String = "$key::shard:$index"
}
