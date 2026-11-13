package com.hoppingmall.dlq.publisher

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [KafkaDLQPublisherAutoConfiguration::class])
class DLQPublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DLQMessagePublisher::class)
    fun noOpDLQMessagePublisher(): NoOpDLQMessagePublisher = NoOpDLQMessagePublisher()
}
