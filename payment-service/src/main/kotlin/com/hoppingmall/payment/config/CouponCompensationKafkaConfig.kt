package com.hoppingmall.payment.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
@Profile("!test")
class CouponCompensationKafkaConfig {

    @Value("\${spring.kafka.consumer.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Bean
    fun couponCompensationProducerFactory(): ProducerFactory<String, String> {
        val configs = HashMap<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configs[ProducerConfig.ACKS_CONFIG] = "all"
        configs[ProducerConfig.RETRIES_CONFIG] = Int.MAX_VALUE
        configs[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        configs[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 120_000
        configs[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 30_000
        configs[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"
        return DefaultKafkaProducerFactory(configs)
    }

    @Bean("couponCompensationKafkaTemplate")
    fun couponCompensationKafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(couponCompensationProducerFactory())
    }

    @Bean
    fun couponCompensationConsumerFactory(): ConsumerFactory<String, String> {
        val configs = HashMap<String, Any>()
        configs[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ConsumerConfig.GROUP_ID_CONFIG] = "coupon-compensation-consumer"
        configs[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configs[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configs[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = "read_committed"
        configs[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 50
        return DefaultKafkaConsumerFactory(configs)
    }

    @Bean("couponCompensationListenerContainerFactory")
    fun couponCompensationListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = couponCompensationConsumerFactory()
        factory.setConcurrency(2)
        return factory
    }
}
