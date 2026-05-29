package com.hoppingmall.cache.serialization

import com.github.benmanes.caffeine.cache.Caffeine
import com.hoppingmall.cache.CachePolicy
import com.hoppingmall.cache.CacheValueSerializer
import com.hoppingmall.cache.FakeLockProvider
import com.hoppingmall.cache.TwoLevelCache
import com.hoppingmall.cache.TwoLevelCacheManager
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import redis.embedded.RedisServer
import java.math.BigDecimal
import java.net.ServerSocket
import java.time.Duration
import java.time.LocalDateTime

/**
 * 캐시 값 직렬화(옵션 A2: 캐시별 타입드 직렬화)의 실 Redis(임베디드) 관측.
 *
 * 적용 전(Step 1 관측): 동일 구조의 비Serializable data class는 JDK 직렬화에서 SerializationException → L2 no-op.
 * 적용 후(이 테스트): @class 없이 선언 타입으로 왕복되고, 인스턴스 간 L2 공유와 null 캐싱이 실제로 동작.
 *
 * embedded redis 미기동 환경에서는 assumeTrue로 skip. (직렬화기 로직 자체는 redis 불필요한
 * TypedCacheValueSerializerTest가 CI에서 항상 검증한다.)
 */
@DisplayName("캐시 값 타입드 직렬화 적용 후 동작 (A2)")
@DisplayNameGeneration(ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheValueSerializationReproductionTest {

    private var redisServer: RedisServer? = null
    private var redisUp = false
    private lateinit var connectionFactory: LettuceConnectionFactory

    enum class FakeStatus { ON_SALE, SOLD_OUT }

    // ProductResponse와 동일한 형태(LocalDateTime/BigDecimal/enum/List), Serializable 미구현.
    data class FakeProductValue(
        val id: Long,
        val name: String,
        val price: BigDecimal,
        val status: FakeStatus,
        val imageUrls: List<String>,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime?
    )

    private val policies = mapOf(
        "product" to CachePolicy(
            cacheName = "product",
            l1MaxSize = 500,
            l1Ttl = Duration.ofSeconds(600),
            l2Ttl = Duration.ofMinutes(30),
            hotKeyThreshold = 50,
            valueType = CacheValueSerializer.typeOf(FakeProductValue::class.java)
        ),
        "product-list" to CachePolicy(
            cacheName = "product-list",
            l1MaxSize = 100,
            l1Ttl = Duration.ofSeconds(600),
            l2Ttl = Duration.ofMinutes(30),
            valueType = CacheValueSerializer.listOf(FakeProductValue::class.java)
        )
    )

    private fun sampleValue(id: Long = 1L) = FakeProductValue(
        id = id,
        name = "상품$id",
        price = BigDecimal("19900"),
        status = FakeStatus.ON_SALE,
        imageUrls = listOf("a.jpg", "b.jpg"),
        createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        updatedAt = null
    )

    @BeforeAll
    fun startRedis() {
        try {
            val port = ServerSocket(0).use { it.localPort }
            redisServer = RedisServer(port).also { it.start() }
            connectionFactory = LettuceConnectionFactory(RedisStandaloneConfiguration("localhost", port)).apply {
                afterPropertiesSet()
                start()
            }
            redisUp = true
        } catch (e: Exception) {
            redisUp = false
        }
    }

    @AfterAll
    fun stopRedis() {
        if (::connectionFactory.isInitialized) runCatching { connectionFactory.destroy() }
        runCatching { redisServer?.stop() }
    }

    private fun cacheManager(): RedisCacheManager =
        TwoLevelCacheManager.buildRedisCacheManager(connectionFactory, policies).apply { afterPropertiesSet() }

    @Test
    fun 비Serializable_data_class가_타입드_직렬화로_L2에_저장되고_복원된다() {
        assumeTrue(redisUp, "embedded redis 미기동 → skip")
        val l2 = cacheManager().getCache("product")!!

        l2.put("product:1", sampleValue())

        assertThat(l2.get("product:1")?.get()).isEqualTo(sampleValue())
    }

    @Test
    fun 컬렉션_값도_제네릭_타입으로_왕복된다() {
        assumeTrue(redisUp, "embedded redis 미기동 → skip")
        val l2 = cacheManager().getCache("product-list")!!
        val list = listOf(sampleValue(1L), sampleValue(2L))

        l2.put("list:1", list)

        assertThat(l2.get("list:1")?.get()).isEqualTo(list)
    }

    @Test
    fun 한_인스턴스가_put하면_다른_인스턴스가_L2를_통해_읽는다_공유_입증() {
        assumeTrue(redisUp, "embedded redis 미기동 → skip")
        val sharedL2 = cacheManager().getCache("product")!!
        val policy = policies["product"]!!
        val registry = SimpleMeterRegistry()

        fun instance() = TwoLevelCache(
            "product",
            Caffeine.newBuilder().maximumSize(policy.l1MaxSize).expireAfterWrite(policy.l1Ttl).build(),
            sharedL2, policy, FakeLockProvider(), null, null, registry
        )

        val instanceA = instance()
        val instanceB = instance() // 같은 L2(Redis), 다른 L1(Caffeine)

        instanceA.put("product:2", sampleValue())

        assertThat(instanceB.get("product:2")?.get()).isEqualTo(sampleValue())
        assertThat(registry.find("cache.l2.failure").counter()?.count() ?: 0.0).isEqualTo(0.0)
    }

    @Test
    fun null_캐싱이_왕복되어_다음_get이_재로드없이_캐시된_null을_반환한다() {
        assumeTrue(redisUp, "embedded redis 미기동 → skip")
        val l2 = cacheManager().getCache("product")!!

        l2.put("product:null", null) // Spring NullValue 저장 (기본 null 캐싱 허용)

        val wrapper = l2.get("product:null")
        assertThat(wrapper).isNotNull // 캐시 미스가 아님 = 미싱키 stampede 위험 없음
        assertThat(wrapper!!.get()).isNull() // 캐시된 null
    }
}
