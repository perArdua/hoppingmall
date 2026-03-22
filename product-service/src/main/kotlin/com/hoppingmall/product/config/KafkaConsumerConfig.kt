package com.hoppingmall.product.config

import com.hoppingmall.product.config.kafka.BusinessContextConsumerInterceptor
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.util.backoff.FixedBackOff

@Configuration
@Profile("!test")
class KafkaConsumerConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${spring.kafka.consumer.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.consumer.group-id:product-service}")
    private lateinit var groupId: String

    @Value("\${spring.kafka.consumer.auto-offset-reset:earliest}")
    private lateinit var autoOffsetReset: String

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val configProps = HashMap<String, Any>()
        configProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
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
