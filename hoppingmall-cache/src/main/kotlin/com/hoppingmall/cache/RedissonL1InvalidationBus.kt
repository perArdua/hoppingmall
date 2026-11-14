package com.hoppingmall.cache

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.redisson.api.RTopic
import org.redisson.api.RedissonClient
import org.redisson.api.listener.BaseStatusListener
import org.redisson.client.codec.StringCodec
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.UUID

/**
 * evict·clear를 Redis pub/sub으로 다른 인스턴스에 알리고, 받은 무효화를 자기 L1에만 적용한다.
 * 구독이 (재)연결될 때마다 L1을 통째로 비운다 — 끊긴 동안 놓친 메시지가 있을 수 있어서다.
 * Redis가 죽어 발행이 실패하면 로그만 남기고, 다른 인스턴스 L1은 L1 TTL까지 옛 값을 줄 수 있다.
 */
class RedissonL1InvalidationBus(
    redissonClient: RedissonClient,
    topicName: String,
    private val cacheManager: TwoLevelCacheManager,
    val instanceId: String = UUID.randomUUID().toString()
) : L1InvalidationPublisher, Closeable {

    private val log = LoggerFactory.getLogger(RedissonL1InvalidationBus::class.java)
    private val topic: RTopic = redissonClient.getTopic(topicName, StringCodec.INSTANCE)
    private val listenerIds = mutableListOf<Int>()

    fun start() {
        listenerIds += topic.addListener(object : BaseStatusListener() {
            override fun onSubscribe(channel: String) {
                cacheManager.clearAllLocal()
            }
        })
        listenerIds += topic.addListener(String::class.java) { _, payload -> handle(payload) }
    }

    override fun publishEvict(cacheName: String, key: Any) {
        val type = KEY_TYPES.entries.firstOrNull { it.value.isInstance(key) }?.key
        publish(Message(instanceId, cacheName, type, type?.let { key.toString() }))
    }

    override fun publishClear(cacheName: String) {
        publish(Message(instanceId, cacheName, null, null))
    }

    fun handle(payload: String) {
        val message = try {
            mapper.readValue<Message>(payload)
        } catch (e: Exception) {
            log.warn("L1 무효화 메시지 해석 실패: {}", e.message)
            return
        }
        if (message.origin == instanceId) return
        val key = decodeKey(message)
        if (key == null) {
            cacheManager.clearLocal(message.cacheName)
        } else {
            cacheManager.evictLocal(message.cacheName, key)
        }
    }

    override fun close() {
        runCatching { topic.removeListener(*listenerIds.toTypedArray()) }
        listenerIds.clear()
    }

    private fun publish(message: Message) {
        try {
            topic.publishAsync(mapper.writeValueAsString(message)).whenComplete { _, e ->
                if (e != null) warnPublishFailure(message, e)
            }
        } catch (e: Exception) {
            warnPublishFailure(message, e)
        }
    }

    private fun warnPublishFailure(message: Message, e: Throwable) {
        log.warn("L1 무효화 발행 실패 [cache={}, key={}]: {}", message.cacheName, message.key, e.message)
    }

    private fun decodeKey(message: Message): Any? {
        val key = message.key ?: return null
        return when (message.keyType) {
            LONG -> key.toLongOrNull()
            INT -> key.toIntOrNull()
            STRING -> key
            else -> null
        }
    }

    data class Message(
        val origin: String,
        val cacheName: String,
        val keyType: String?,
        val key: String?
    )

    companion object {
        private const val LONG = "long"
        private const val INT = "int"
        private const val STRING = "string"
        private val KEY_TYPES: Map<String, Class<*>> = linkedMapOf(
            LONG to Long::class.javaObjectType,
            INT to Int::class.javaObjectType,
            STRING to String::class.java
        )
        private val mapper = jacksonObjectMapper()
    }
}
