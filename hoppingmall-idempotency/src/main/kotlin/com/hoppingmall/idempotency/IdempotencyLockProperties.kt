package com.hoppingmall.idempotency

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "idempotency.lock")
data class IdempotencyLockProperties(
    // Redisson lockWatchdogTimeout. 처리 중에는 이 값의 1/3마다 락을 연장하고, 락을 쥔 인스턴스가 죽으면 이 시간 뒤 풀린다.
    // 기본 30초에서 10초로 줄였으므로 GC 정지·네트워크 끊김으로 연장이 10초 넘게 멈추면 처리 중에도 락을 잃어
    // 같은 키의 다른 요청이 동시에 처리될 수 있다.
    val watchdogTimeout: Duration = Duration.ofSeconds(10)
)
