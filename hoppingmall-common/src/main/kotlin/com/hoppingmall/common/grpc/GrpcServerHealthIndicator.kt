package com.hoppingmall.common.grpc

import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator

class GrpcServerHealthIndicator(
    private val lifecycles: ObjectProvider<GrpcServerLifecycle>
) : HealthIndicator {

    override fun health(): Health {
        val servers = lifecycles.orderedStream().toList()
        val running = servers.count { it.isRunning }
        val builder = if (running == servers.size) Health.up() else Health.outOfService()
        return builder
            .withDetail("servers", servers.size)
            .withDetail("running", running)
            .build()
    }
}
