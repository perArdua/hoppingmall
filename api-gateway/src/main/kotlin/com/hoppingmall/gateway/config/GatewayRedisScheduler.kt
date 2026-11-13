package com.hoppingmall.gateway.config

import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

object GatewayRedisScheduler {
    val instance: Scheduler = Schedulers.newBoundedElastic(
        Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE,
        Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
        "gateway-redis",
        60,
        true
    )
}
