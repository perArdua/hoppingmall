package com.hoppingmall.common.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("docker")
@ConditionalOnClass(RedissonClient::class)
class RedissonDockerConfig(
    @Value("\${spring.data.redis.cluster.nodes:redis-node-1:7001,redis-node-2:7002,redis-node-3:7003}")
    private val clusterNodes: List<String>
) {

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        val clusterConfig = config.useClusterServers()
        clusterConfig.scanInterval = 1000
        clusterNodes.forEach { node ->
            clusterConfig.addNodeAddress("redis://$node")
        }
        return Redisson.create(config)
    }
}
