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

    fun createClient(): RedissonClient {
        val config = Config()
        config.useSingleServer()
            .setAddress("redis://${container.host}:${container.getMappedPort(REDIS_PORT)}")
        return Redisson.create(config)
    }
}
