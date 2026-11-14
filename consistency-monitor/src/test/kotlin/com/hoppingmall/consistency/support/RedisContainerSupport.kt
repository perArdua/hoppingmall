package com.hoppingmall.consistency.support

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

object RedisContainerSupport {

    private const val REDIS_PORT = 6379

    private val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(REDIS_PORT)
            .also { it.start() }
    }

    val host: String get() = container.host

    val port: Int get() = container.getMappedPort(REDIS_PORT)
}
