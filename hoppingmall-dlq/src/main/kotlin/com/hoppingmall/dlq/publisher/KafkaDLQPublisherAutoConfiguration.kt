package com.hoppingmall.dlq.publisher

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate

@AutoConfiguration(beforeName = ["org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"])
@ConditionalOnClass(KafkaTemplate::class)
class KafkaDLQPublisherAutoConfiguration : DisposableBean {

    private var republishProducerFactory: DefaultKafkaProducerFactory<String, Any>? = null

    @Bean
    @ConditionalOnBean(KafkaTemplate::class)
    fun kafkaDLQMessagePublisher(kafkaTemplate: KafkaTemplate<String, Any>): KafkaDLQMessagePublisher =
        KafkaDLQMessagePublisher(kafkaTemplate)

    @Bean
    @ConditionalOnMissingBean(DLQMessagePublisher::class, KafkaTemplate::class)
    @ConditionalOnSingleCandidate(ConsumerFactory::class)
    fun consumerAlignedKafkaDLQMessagePublisher(consumerFactory: ConsumerFactory<*, *>): KafkaDLQMessagePublisher {
        val producerFactory = DefaultKafkaProducerFactory<String, Any>(
            republishProducerConfig(consumerFactory.configurationProperties)
        )
        republishProducerFactory = producerFactory
        return KafkaDLQMessagePublisher(KafkaTemplate(producerFactory))
    }

    override fun destroy() {
        republishProducerFactory?.destroy()
    }

    companion object {
        private const val SCHEMA_REGISTRY_URL = "schema.registry.url"
        private const val VALUE_SUBJECT_NAME_STRATEGY = "value.subject.name.strategy"
        private const val MAX_BLOCK_MS = 5_000

        fun republishProducerConfig(consumerConfig: Map<String, Any>): Map<String, Any> {
            val config = HashMap<String, Any>()
            consumerConfig[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]?.let {
                config[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = it
            }
            consumerConfig[SCHEMA_REGISTRY_URL]?.let { config[SCHEMA_REGISTRY_URL] = it }
            config[VALUE_SUBJECT_NAME_STRATEGY] = TopicRecordNameStrategy::class.java.name
            config[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            config[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
            config[ProducerConfig.ACKS_CONFIG] = "all"
            config[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
            config[ProducerConfig.MAX_BLOCK_MS_CONFIG] = MAX_BLOCK_MS
            return config
        }
    }
}
