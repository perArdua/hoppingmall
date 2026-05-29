package com.hoppingmall.cache

import com.fasterxml.jackson.databind.JavaType
import java.time.Duration

data class CachePolicy(
    val cacheName: String,
    val l1MaxSize: Long,
    val l1Ttl: Duration,
    val l2Ttl: Duration,
    val jitterPercent: Int = 10,
    val hotKeyThreshold: Long = 0L,
    val hotKeyWindow: Duration = Duration.ofSeconds(60),
    val hotKeyShardCount: Int = 4,
    // L2 값의 타입드 직렬화 대상. 서비스 CacheConfig가 CacheValueSerializer.typeOf/listOf로 선언한다.
    // null이면 미선언 캐시로 보고 fallback(@class) 직렬화기를 사용한다.
    val valueType: JavaType? = null
) {
    val dynamicHotKeyEnabled: Boolean get() = hotKeyThreshold > 0L
}
