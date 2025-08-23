package com.hoppingmall.mall.global.common.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.transaction.KafkaTransactionManager
import org.mockito.kotlin.mock

@TestConfiguration
class TestKafkaConfig {

    @Bean
    @Primary
    fun testKafkaTemplate(): KafkaTemplate<String, Any> {
        return mock()
    }

    @Bean("kafkaTransactionManager")
    @Primary
    fun testKafkaTransactionManager(): KafkaTransactionManager<String, Any> {
        return mock()
    }
}