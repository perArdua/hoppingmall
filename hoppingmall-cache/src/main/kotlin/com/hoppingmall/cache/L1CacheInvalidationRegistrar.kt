package com.hoppingmall.cache

import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.cache.CacheManager

class L1CacheInvalidationRegistrar(
    private val cacheManagers: ObjectProvider<CacheManager>,
    private val redissonClient: ObjectProvider<RedissonClient>,
    private val topicName: String
) : SmartInitializingSingleton, DisposableBean {

    private val log = LoggerFactory.getLogger(L1CacheInvalidationRegistrar::class.java)
    private val buses = mutableListOf<Pair<TwoLevelCacheManager, RedissonL1InvalidationBus>>()

    override fun afterSingletonsInstantiated() {
        val managers = cacheManagers.orderedStream().toList().filterIsInstance<TwoLevelCacheManager>()
        if (managers.isEmpty()) return
        val client = redissonClient.ifUnique
        if (client == null) {
            log.warn("RedissonClient가 없어 L1 캐시 무효화를 다른 인스턴스에 전파하지 않음 [topic={}]", topicName)
            return
        }
        managers.forEach { manager ->
            val bus = RedissonL1InvalidationBus(client, topicName, manager)
            bus.start()
            manager.attachInvalidationPublisher(bus)
            buses += manager to bus
        }
    }

    override fun destroy() {
        buses.forEach { (manager, bus) ->
            manager.attachInvalidationPublisher(null)
            bus.close()
        }
        buses.clear()
    }
}
