package com.hoppingmall.order.config

import com.hoppingmall.order.config.kafka.BusinessContextConsumerInterceptor
import com.hoppingmall.order.config.kafka.BusinessContextProducerInterceptor
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.util.backoff.FixedBackOff
import java.util.UUID

@Configuration
@Profile("!test")
class KafkaConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${spring.kafka.producer.bootstrap-servers:\${spring.kafka.consumer.bootstrap-servers}}")
    private lateinit var producerBootstrapServers: String

    @Value("\${spring.kafka.consumer.bootstrap-servers}")
    private lateinit var consumerBootstrapServers: String

    @Value("\${spring.kafka.consumer.group-id:order-service}")
    private lateinit var groupId: String

    @Value("\${spring.kafka.consumer.auto-offset-reset:earliest}")
    private lateinit var autoOffsetReset: String

    private val instanceId: String = UUID.randomUUID().toString()

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = HashMap<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = producerBootstrapServers
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java

        configProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configProps[ProducerConfig.ACKS_CONFIG] = "all"
        configProps[ProducerConfig.RETRIES_CONFIG] = Int.MAX_VALUE
        configProps[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        configProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = "order-tx-$instanceId"

        configProps[ProducerConfig.BATCH_SIZE_CONFIG] = 16384
        configProps[ProducerConfig.LINGER_MS_CONFIG] = 10
        configProps[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"

        configProps[ProducerConfig.INTERCEPTOR_CLASSES_CONFIG] = listOf(BusinessContextProducerInterceptor::class.java.name)

        val producerFactory = DefaultKafkaProducerFactory<String, Any>(configProps)
        producerFactory.setTransactionIdPrefix("order-tx-$instanceId-")
        return producerFactory
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        val template = KafkaTemplate(producerFactory())
        template.setTransactionIdPrefix("order-tx-$instanceId-")
        return template
    }

    @Bean("kafkaTransactionManager")
    fun kafkaTransactionManager(): org.springframework.kafka.transaction.KafkaTransactionManager<String, Any> {
        return org.springframework.kafka.transaction.KafkaTransactionManager(producerFactory())
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val configProps = HashMap<String, Any>()
        configProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = consumerBootstrapServers
        configProps[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        configProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = autoOffsetReset
        configProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java

        configProps[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configProps[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = "read_committed"

        configProps[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = 300_000
        configProps[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
        configProps[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = 30_000
        configProps[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = 10_000

        return DefaultKafkaConsumerFactory(configProps)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.setConcurrency(4)
        factory.setRecordInterceptor(BusinessContextConsumerInterceptor())

        val errorHandler = DefaultErrorHandler(
            { record, exception ->
                log.error("Kafka 메시지 처리 실패 (DLQ): topic={}, offset={}, error={}",
                    record.topic(), record.offset(), exception.message)
            },
            FixedBackOff(1000L, 3)
        )
        errorHandler.addNotRetryableExceptions(
            DeserializationException::class.java,
            SerializationException::class.java,
            MessageConversionException::class.java
        )
        factory.setCommonErrorHandler(errorHandler)

        return factory
    }
}
