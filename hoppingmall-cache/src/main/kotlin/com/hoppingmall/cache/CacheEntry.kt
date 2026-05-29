package com.hoppingmall.cache

/**
 * 핫키 L1/L2 저장 값을 감싸는 메타데이터 래퍼 (XFetch 논리 만료용).
 *
 * - value: 실제 캐시 값 (store-value; null 값은 래핑하지 않고 NullValue로 별도 저장)
 * - cachedAtEpochMs: 적재 시각(ms)
 * - physicalExpireAtEpochMs: 물리 TTL 만료 예정 시각(ms) — 페일세이프 경계
 * - lastLoadDurationMs: 직전 로드 소요시간(ms) — XFetch의 delta
 *
 * dynamicHotKeyEnabled 캐시(현재 product)에만 적용되며, 타입드 직렬화는
 * CacheValueSerializer.entryOf(valueType)로 CacheEntry<ValueType>을 왕복한다.
 */
data class CacheEntry<T>(
    val value: T,
    val cachedAtEpochMs: Long,
    val physicalExpireAtEpochMs: Long,
    val lastLoadDurationMs: Long
)
