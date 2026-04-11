package com.hoppingmall.user.config

import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.common.config.kafka.BusinessContextConsumerInterceptor
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
class KafkaConsumerConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id}") private val groupId: String,
    @Value("\${spring.kafka.properties.schema.registry.url:http://localhost:8081}") private val schemaRegistryUrl: String,
    @org.springframework.context.annotation.Lazy private val dlqCommandService: DLQCommandService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val props = mutableMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to AvroJsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed",
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to 300_000,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100,
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30_000,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10_000,
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to 1024,
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to 500,
            "schema.registry.url" to schemaRegistryUrl
        )
        return DefaultKafkaConsumerFactory(props)
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
                val deadLetterMessage = DeadLetterMessage(
                    originalTopic = record.topic(),
                    originalPartition = record.partition(),
                    originalOffset = record.offset(),
                    originalKey = record.key()?.toString(),
                    originalValue = record.value()?.toString(),
                    exception = exception.message,
                    timestamp = System.currentTimeMillis()
                )
                dlqCommandService.saveDLQMessage(deadLetterMessage)
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
