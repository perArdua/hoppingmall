package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.redisson.api.RTopic
import org.redisson.api.RedissonClient
import org.redisson.api.listener.MessageListener
import org.redisson.api.listener.StatusListener
import org.redisson.client.codec.StringCodec
import org.redisson.misc.CompletableFutureWrapper
import org.springframework.cache.interceptor.SimpleKey

@DisplayName("RedissonL1InvalidationBus")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RedissonL1InvalidationBusTest {

    private val redissonClient: RedissonClient = mock()
    private val topic: RTopic = mock()
    private val senderManager: TwoLevelCacheManager = mock()
    private val receiverManager: TwoLevelCacheManager = mock()
    private lateinit var sender: RedissonL1InvalidationBus
    private lateinit var receiver: RedissonL1InvalidationBus

    @BeforeEach
    fun setUp() {
        whenever(redissonClient.getTopic(eq(TOPIC), eq(StringCodec.INSTANCE))).thenReturn(topic)
        whenever(topic.publishAsync(any())).thenReturn(CompletableFutureWrapper(1L))
        sender = RedissonL1InvalidationBus(redissonClient, TOPIC, senderManager, "sender")
        receiver = RedissonL1InvalidationBus(redissonClient, TOPIC, receiverManager, "receiver")
    }

    private fun publishedPayloads(count: Int): List<String> {
        val captor = argumentCaptor<Any>()
        verify(topic, times(count)).publishAsync(captor.capture())
        return captor.allValues.map { it as String }
    }

    @Test
    fun 다른_인스턴스가_낸_키_무효화는_같은_타입의_키로_L1에서_지운다() {
        sender.publishEvict("product", 7L)
        sender.publishEvict("product", 8)
        sender.publishEvict("coupon:available", "all")

        publishedPayloads(3).forEach { receiver.handle(it) }

        verify(receiverManager).evictLocal("product", 7L)
        verify(receiverManager).evictLocal("product", 8)
        verify(receiverManager).evictLocal("coupon:available", "all")
    }

    @Test
    fun 자기가_낸_무효화는_무시한다() {
        sender.publishEvict("product", 7L)
        sender.publishClear("product")

        publishedPayloads(2).forEach { sender.handle(it) }

        verifyNoInteractions(senderManager)
    }

    @Test
    fun 문자열로_되돌릴_수_없는_키는_그_캐시_L1을_통째로_비운다() {
        sender.publishEvict("categories:root", SimpleKey.EMPTY)

        receiver.handle(publishedPayloads(1).single())

        verify(receiverManager).clearLocal("categories:root")
    }

    @Test
    fun 캐시_비우기는_그_캐시_L1을_통째로_비운다() {
        sender.publishClear("point-policy")

        receiver.handle(publishedPayloads(1).single())

        verify(receiverManager).clearLocal("point-policy")
    }

    @Test
    fun 숫자가_아닌_Long_키는_그_캐시_L1을_통째로_비운다() {
        receiver.handle(message(keyType = "long", key = "x"))

        verify(receiverManager).clearLocal("product")
    }

    @Test
    fun 해석할_수_없는_메시지는_무시한다() {
        receiver.handle("not-json")

        verifyNoInteractions(receiverManager)
    }

    @Test
    fun Redis가_죽어_발행이_실패해도_예외를_던지지_않는다() {
        whenever(topic.publishAsync(any()))
            .thenReturn(CompletableFutureWrapper<Long>(IllegalStateException("redis down")))
            .thenThrow(IllegalStateException("redis down"))

        assertThatCode {
            sender.publishEvict("product", 7L)
            sender.publishClear("product")
        }.doesNotThrowAnyException()
    }

    @Test
    fun 구독이_다시_맺어지면_L1을_통째로_비우고_닫으면_리스너를_뗀다() {
        whenever(topic.addListener(any<StatusListener>())).thenReturn(1)
        whenever(topic.addListener(eq(String::class.java), any<MessageListener<String>>())).thenReturn(2)
        receiver.start()

        val statusCaptor = argumentCaptor<StatusListener>()
        verify(topic).addListener(statusCaptor.capture())
        statusCaptor.firstValue.onSubscribe(TOPIC)
        verify(receiverManager).clearAllLocal()

        val messageCaptor = argumentCaptor<MessageListener<String>>()
        verify(topic).addListener(eq(String::class.java), messageCaptor.capture())
        messageCaptor.firstValue.onMessage(TOPIC, message(keyType = "long", key = "3"))
        verify(receiverManager).evictLocal("product", 3L)

        receiver.close()
        verify(topic).removeListener(1, 2)
    }

    private fun message(keyType: String, key: String): String =
        "{\"origin\":\"sender\",\"cacheName\":\"product\",\"keyType\":\"$keyType\",\"key\":\"$key\"}"

    companion object {
        private const val TOPIC = "cache:l1-invalidation:test"
    }
}
