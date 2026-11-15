package com.hoppingmall.user.support

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

object RedisContainerSupport {

    private const val REDIS_PORT = 6379

    private val container: GenericContainer<*> by lazy {
        GenericContainer("redis:7.2-alpine")
            .withExposedPorts(REDIS_PORT)
            .also { it.start() }
    }

    val host: String get() = container.host

    val port: Int get() = container.getMappedPort(REDIS_PORT)

    fun address(): String = "redis://$host:$port"

    fun createClient(): RedissonClient {
        val config = Config()
        config.useSingleServer()
            .setAddress(address())
        return Redisson.create(config)
    }
}
