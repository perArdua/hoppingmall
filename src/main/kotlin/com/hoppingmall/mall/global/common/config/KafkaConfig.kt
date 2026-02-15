package com.hoppingmall.mall.global.common.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.util.backoff.FixedBackOff
import com.hoppingmall.mall.global.common.service.DLQService
import org.apache.kafka.clients.consumer.ConsumerConfig.ISOLATION_LEVEL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG
import org.springframework.messaging.converter.MessageConversionException
import org.apache.kafka.common.errors.SerializationException
import org.springframework.kafka.support.serializer.DeserializationException
import java.util.UUID
@Configuration
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.consumer.group-id:hoppingmall-group}")
    private lateinit var groupId: String

    @Value("\${spring.kafka.consumer.auto-offset-reset:earliest}")
    private lateinit var autoOffsetReset: String

    @Value("\${spring.kafka.listener.concurrency:1}")
    private var concurrency: Int = 4
    
    private val instanceId: String = UUID.randomUUID().toString()
    private val trustedPackages: String = listOf(
        "com.hoppingmall.mall.payment.dto.event",
        "com.hoppingmall.mall.notification.dto.event"
    ).joinToString(",")

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = HashMap<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        
        // EOS 설정 - 완전한 트랜잭션 지원
        configProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configProps[ProducerConfig.ACKS_CONFIG] = "all"
        configProps[ProducerConfig.RETRIES_CONFIG] = Int.MAX_VALUE
        configProps[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        configProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = "hoppingmall-tx-$instanceId"
        
        // 성능 최적화
        configProps[ProducerConfig.BATCH_SIZE_CONFIG] = 16384
        configProps[ProducerConfig.LINGER_MS_CONFIG] = 10
        configProps[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"
        
        val producerFactory = DefaultKafkaProducerFactory<String, Any>(configProps)
        producerFactory.setTransactionIdPrefix("hoppingmall-tx-$instanceId-")
        return producerFactory
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        val template = KafkaTemplate(producerFactory())
        template.setTransactionIdPrefix("hoppingmall-tx-$instanceId-")
        return template
    }

    @Bean("kafkaTransactionManager")
    fun kafkaTransactionManager(): org.springframework.kafka.transaction.KafkaTransactionManager<String, Any> {
        return org.springframework.kafka.transaction.KafkaTransactionManager(producerFactory())
    }


    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val configProps = HashMap<String, Any>()
        configProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        configProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = autoOffsetReset
        configProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java
        configProps[JsonDeserializer.TRUSTED_PACKAGES] = trustedPackages
        
        // Consumer 설정 - EOS를 위한 read_committed 유지
        configProps[ENABLE_AUTO_COMMIT_CONFIG] = false
        configProps[ISOLATION_LEVEL_CONFIG] = "read_committed"

        configProps[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = 300_000
        configProps[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
        configProps[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = 30_000
        configProps[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = 10_000

        return DefaultKafkaConsumerFactory(configProps)
    }

    @Bean
    fun kafkaListenerContainerFactory(dlqService: DLQService): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.setConcurrency(concurrency)
        
        // DLQ 에러 핸들러 설정
        val errorHandler = DefaultErrorHandler(
            { record, exception ->
                // 실패한 메시지를 DB에 저장
                sendToDLQ(record, exception, dlqService)
            },
            FixedBackOff(1000L, 3) // 1초 간격으로 3번 재시도
        )
        errorHandler.addNotRetryableExceptions(
            DeserializationException::class.java,
            SerializationException::class.java,
            MessageConversionException::class.java
        )
        factory.setCommonErrorHandler(errorHandler)
        
        return factory
    }

    private fun sendToDLQ(
        record: org.apache.kafka.clients.consumer.ConsumerRecord<*, *>, 
        exception: Exception,
        dlqService: DLQService
    ) {
        try {
            val dlqMessage = DeadLetterMessage(
                originalTopic = record.topic(),
                originalPartition = record.partition(),
                originalOffset = record.offset(),
                originalKey = record.key()?.toString(),
                originalValue = record.value()?.toString(),
                exception = exception.message,
                timestamp = System.currentTimeMillis()
            )
            
            // DB에 DLQ 메시지 저장
            dlqService.saveDLQMessage(dlqMessage)
        } catch (e: Exception) {
            // DLQ 저장 실패 시에도 메시지 손실 방지를 위해 로깅만 수행
            println("DLQ 저장 실패: ${e.message}")
        }
    }
}

data class DeadLetterMessage(
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val originalKey: String?,
    val originalValue: String?,
    val exception: String?,
    val timestamp: Long
) 
