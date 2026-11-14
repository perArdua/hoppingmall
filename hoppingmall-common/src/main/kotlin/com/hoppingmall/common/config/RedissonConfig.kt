package com.hoppingmall.common.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("docker")
@ConditionalOnClass(RedissonClient::class, RedissonAutoConfigurationCustomizer::class)
class RedissonDockerConfig(
    @Value("\${spring.data.redis.cluster.nodes:redis-node-1:7001,redis-node-2:7002,redis-node-3:7003}")
    private val clusterNodes: List<String>,
    private val customizers: ObjectProvider<RedissonAutoConfigurationCustomizer>
) {

    fun clientConfig(): Config {
        val config = Config()
        val clusterConfig = config.useClusterServers()
        clusterConfig.scanInterval = 1000
        clusterNodes.forEach { node ->
            clusterConfig.addNodeAddress("redis://$node")
        }
        customizers.orderedStream().forEach { it.customize(config) }
        return config
    }

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient = Redisson.create(clientConfig())
}
