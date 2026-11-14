package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCache
import java.time.Duration

@DisplayName("TwoLevelCacheManager 자기 L1 무효화")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheManagerLocalInvalidationTest {

    private val l2Product = ConcurrentMapCache("product")
    private val l2Category = ConcurrentMapCache("category")
    private val publisher: L1InvalidationPublisher = mock()

    private fun manager(): TwoLevelCacheManager {
        val policies = listOf("product", "category").associateWith {
            CachePolicy(it, 10, Duration.ofMinutes(10), Duration.ofMinutes(30))
        }
        val redisCacheManager: CacheManager = mock()
        whenever(redisCacheManager.getCache("product")).thenReturn(l2Product)
        whenever(redisCacheManager.getCache("category")).thenReturn(l2Category)
        whenever(redisCacheManager.getCache("plain")).thenReturn(ConcurrentMapCache("plain"))
        return TwoLevelCacheManager(redisCacheManager, policies, HotKeyDetectorRegistry(policies.values))
            .also { it.attachInvalidationPublisher(publisher) }
    }

    @Test
    fun 키_무효화는_L1만_지우고_L2와_발행은_건드리지_않는다() {
        val manager = manager()
        val cache = manager.getCache("product")!!
        cache.get(1L) { "v1" }
        l2Product.put(1L, "v2")

        manager.evictLocal("product", 1L)

        assertThat(cache.get(1L)?.get()).isEqualTo("v2")
        verifyNoInteractions(publisher)
    }

    @Test
    fun 캐시_비우기와_전체_비우기는_L1만_비운다() {
        val manager = manager()
        val product = manager.getCache("product")!!
        val category = manager.getCache("category")!!
        product.get(1L) { "p1" }
        category.get(1L) { "c1" }
        l2Product.put(1L, "p2")
        l2Category.put(1L, "c2")

        manager.clearLocal("product")
        assertThat(product.get(1L)?.get()).isEqualTo("p2")
        assertThat(category.get(1L)?.get()).isEqualTo("c1")

        manager.clearAllLocal()
        assertThat(category.get(1L)?.get()).isEqualTo("c2")
        verifyNoInteractions(publisher)
    }

    @Test
    fun 만들지_않은_캐시나_두_단계가_아닌_캐시는_무시한다() {
        val manager = manager()
        manager.getCache("plain")

        assertThatCode {
            manager.evictLocal("missing", 1L)
            manager.evictLocal("plain", 1L)
            manager.clearLocal("plain")
        }.doesNotThrowAnyException()
    }

    @Test
    fun 캐시에서_무효화하면_다른_인스턴스에_알린다() {
        val manager = manager()
        val cache = manager.getCache("product")!!
        val published = mutableListOf<String>()
        manager.attachInvalidationPublisher(object : L1InvalidationPublisher {
            override fun publishEvict(cacheName: String, key: Any) {
                published += "evict:$cacheName:$key"
            }

            override fun publishClear(cacheName: String) {
                published += "clear:$cacheName"
            }
        })

        cache.evict(1L)
        cache.clear()

        assertThat(published).containsExactly("evict:product:1", "clear:product")
    }
}
