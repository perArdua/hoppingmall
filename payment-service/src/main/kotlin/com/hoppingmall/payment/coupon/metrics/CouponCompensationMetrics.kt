package com.hoppingmall.payment.coupon.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(MeterRegistry::class)
class CouponCompensationMetrics(
    meterRegistry: MeterRegistry
) {

    private val syncSuccessCounter: Counter = Counter.builder("coupon.compensation.sync.success")
        .description("쿠폰 동기 보상 성공 수")
        .register(meterRegistry)

    private val syncFailureCounter: Counter = Counter.builder("coupon.compensation.sync.failure")
        .description("쿠폰 동기 보상 실패 수")
        .register(meterRegistry)

    private val asyncPublishedCounter: Counter = Counter.builder("coupon.compensation.async.published")
        .description("쿠폰 비동기 보상 이벤트 발행 수")
        .register(meterRegistry)

    private val asyncConsumedCounter: Counter = Counter.builder("coupon.compensation.async.consumed")
        .description("쿠폰 비동기 보상 이벤트 처리 수")
        .register(meterRegistry)

    private val dlqCounter: Counter = Counter.builder("coupon.compensation.dlq")
        .description("쿠폰 보상 DLQ 이동 수")
        .register(meterRegistry)

    fun recordSyncSuccess() = syncSuccessCounter.increment()
    fun recordSyncFailure() = syncFailureCounter.increment()
    fun recordAsyncPublished() = asyncPublishedCounter.increment()
    fun recordAsyncConsumed() = asyncConsumedCounter.increment()
    fun recordDlq() = dlqCounter.increment()
}
