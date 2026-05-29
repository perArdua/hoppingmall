package com.hoppingmall.cache

import java.time.Duration

/**
 * 인스턴스 간 백그라운드 갱신 단일화 가드 (best-effort).
 *
 * 가드를 못 잡은 인스턴스는 stale 값을 서빙하고 갱신을 시도하지 않는다.
 * 가드 상실(TTL 만료 등)로 두 인스턴스가 동시에 갱신해도 로더가 멱등이라 결과는 동일하다.
 */
interface RefreshGuard {
    /**
     * 가드 획득(키가 없을 때만 = SET NX PX). 성공 시 [ttl] 동안 보유하며 명시적으로 release하지 않고
     * TTL로 만료시킨다(같은 창에서 재획득→이중 갱신 방지).
     */
    fun tryAcquire(key: String, ttl: Duration): Boolean

    /**
     * 갱신 실패 시 짧은 [cooldown]만 남겨, 다음 요청이 곧 재시도하되 즉시 버스트하지는 않게 한다
     * (DEL 후 즉시 재획득 시의 thundering herd 방지).
     */
    fun markFailed(key: String, cooldown: Duration)
}
