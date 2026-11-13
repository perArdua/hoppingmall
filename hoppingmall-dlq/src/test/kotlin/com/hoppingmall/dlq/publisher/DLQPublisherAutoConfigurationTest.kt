package com.hoppingmall.dlq.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.context.annotation.ImportCandidates
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.mock.MockProducerFactory
import org.springframework.test.util.ReflectionTestUtils

@DisplayName("DLQ 재발행기 자동 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQPublisherAutoConfigurationTest {

    private val objectMapper = ObjectMapper()

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(SCHEMA_SCOPE)
    }

    @Test
    fun KafkaTemplate_없이_자기_소비자만_둔_서비스에는_그_소비자와_같은_클러스터로_Avro를_보내는_Kafka_재발행기가_뜬다() {
        serviceContext()
            .withUserConfiguration(ServiceConsumerConfig::class.java, DLQPackageScanConfig::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(DLQMessagePublisher::class.java)
                val publisher = context.getBean(DLQMessagePublisher::class.java)
                assertThat(publisher).isInstanceOf(KafkaDLQMessagePublisher::class.java)

                val producerConfig = producerConfigOf(publisher)
                assertThat(producerConfig[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG]).isEqualTo(SERVICE_BOOTSTRAP_SERVERS)
                assertThat(producerConfig["schema.registry.url"]).isEqualTo(SCHEMA_REGISTRY_URL)
                assertThat(producerConfig[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG]).isEqualTo(KafkaAvroSerializer::class.java)
                assertThat(producerConfig["value.subject.name.strategy"]).isEqualTo(TopicRecordNameStrategy::class.java.name)
            }
    }

    @Test
    fun 자동_설정_재발행기가_만든_값을_그_서비스의_소비자가_원래_내용으로_읽는다() {
        serviceContext()
            .withUserConfiguration(ServiceConsumerConfig::class.java, DLQPackageScanConfig::class.java)
            .run { context ->
                val publisher = context.getBean(DLQMessagePublisher::class.java)
                assertThat(publisher).isInstanceOf(KafkaDLQMessagePublisher::class.java)

                val consumed = paymentCompletedJson()
                val republished = AvroEventConverter(objectMapper).convertJsonToAvro("PaymentCompleted", consumed)
                val bytes = valueSerializerOf(publisher).serialize(KafkaTopics.PAYMENT, republished)

                val consumerConfig = context.getBean(ConsumerFactory::class.java).configurationProperties
                val readBack = AvroJsonDeserializer().apply { configure(consumerConfig, false) }
                    .deserialize(KafkaTopics.PAYMENT, bytes)
                assertThat(objectMapper.readTree(readBack)).isEqualTo(objectMapper.readTree(consumed))
            }
    }

    @Test
    fun 서비스가_KafkaTemplate을_직접_두면_그_템플릿으로_재발행한다() {
        serviceContext()
            .withUserConfiguration(
                ServiceKafkaTemplateConfig::class.java,
                ServiceConsumerConfig::class.java,
                DLQPackageScanConfig::class.java
            )
            .run { context ->
                assertThat(context).hasSingleBean(DLQMessagePublisher::class.java)
                val publisher = context.getBean(DLQMessagePublisher::class.java)
                assertThat(publisher).isInstanceOf(KafkaDLQMessagePublisher::class.java)
                assertThat(ReflectionTestUtils.getField(publisher, "kafkaTemplate"))
                    .isSameAs(context.getBean("kafkaTemplate"))
            }
    }

    @Test
    fun 서비스_KafkaTemplate_설정이_DLQ_패키지_스캔보다_늦게_처리돼도_서비스_템플릿으로_재발행한다() {
        serviceContext()
            .withUserConfiguration(
                DLQPackageScanConfig::class.java,
                ServiceKafkaTemplateConfig::class.java
            )
            .run { context ->
                assertThat(context).hasSingleBean(DLQMessagePublisher::class.java)
                val publisher = context.getBean(DLQMessagePublisher::class.java)
                assertThat(publisher).isInstanceOf(KafkaDLQMessagePublisher::class.java)
                assertThat(ReflectionTestUtils.getField(publisher, "kafkaTemplate"))
                    .isSameAs(context.getBean("kafkaTemplate"))
            }
    }

    @Test
    fun 서비스가_Kafka_소비자도_템플릿도_두지_않으면_NoOp_재발행기가_뜬다() {
        serviceContext()
            .withUserConfiguration(DLQPackageScanConfig::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(DLQMessagePublisher::class.java)
                assertThat(context.getBean(DLQMessagePublisher::class.java))
                    .isInstanceOf(NoOpDLQMessagePublisher::class.java)
            }
    }

    private fun serviceContext(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration::class.java, *dlqAutoConfigurations()))

    private fun dlqAutoConfigurations(): Array<Class<*>> =
        ImportCandidates.load(AutoConfiguration::class.java, javaClass.classLoader)
            .filter { it.startsWith("com.hoppingmall.dlq") }
            .map { Class.forName(it) }
            .toTypedArray()

    private fun producerConfigOf(publisher: DLQMessagePublisher): Map<String, Any> {
        val template = ReflectionTestUtils.getField(publisher, "kafkaTemplate") as KafkaTemplate<*, *>
        return template.producerFactory.configurationProperties
    }

    @Suppress("UNCHECKED_CAST")
    private fun valueSerializerOf(publisher: DLQMessagePublisher): Serializer<Any> {
        val producerConfig = producerConfigOf(publisher)
        val serializerClass = producerConfig[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] as Class<*>
        return (serializerClass.getDeclaredConstructor().newInstance() as Serializer<Any>)
            .apply { configure(producerConfig, false) }
    }

    private fun paymentCompletedJson(): String {
        val fields = mapOf(
            "paymentId" to 100L,
            "orderId" to 200L,
            "userId" to 1L,
            "amount" to "15000",
            "pointAmount" to "0",
            "method" to "CREDIT_CARD",
            "status" to "SUCCESS",
            "transactionId" to "tx-1",
            "completedAt" to "2026-11-13T10:00:00",
            "eventType" to "PaymentCompleted"
        )
        val record = AvroEventConverter(objectMapper).convertJsonToAvro("PaymentCompleted", objectMapper.writeValueAsString(fields))
        val serializer = KafkaAvroSerializer().apply {
            configure(
                mapOf(
                    "schema.registry.url" to SCHEMA_REGISTRY_URL,
                    "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
                ),
                false
            )
        }
        return AvroJsonDeserializer().apply { configure(mapOf("schema.registry.url" to SCHEMA_REGISTRY_URL), false) }
            .deserialize(KafkaTopics.PAYMENT, serializer.serialize(KafkaTopics.PAYMENT, record))!!
    }

    @Configuration
    class ServiceConsumerConfig {
        @Bean
        fun consumerFactory(): ConsumerFactory<String, Any> = DefaultKafkaConsumerFactory(
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to SERVICE_BOOTSTRAP_SERVERS,
                ConsumerConfig.GROUP_ID_CONFIG to "service-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to AvroJsonDeserializer::class.java,
                "schema.registry.url" to SCHEMA_REGISTRY_URL,
                "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
            )
        )
    }

    @Configuration
    class ServiceKafkaTemplateConfig {
        @Bean
        fun kafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(
            MockProducerFactory<String, Any> {
                MockProducer(true, StringSerializer(), Serializer<Any> { _, _ -> ByteArray(0) })
            }
        )
    }

    @Configuration
    @ComponentScan(
        basePackageClasses = [DLQMessagePublisher::class],
        excludeFilters = [
            ComponentScan.Filter(type = FilterType.CUSTOM, classes = [AutoConfigurationExcludeFilter::class]),
            ComponentScan.Filter(type = FilterType.REGEX, pattern = [".*Test.*"])
        ]
    )
    class DLQPackageScanConfig

    companion object {
        private const val SCHEMA_SCOPE = "dlq-publisher-auto-configuration"
        private const val SCHEMA_REGISTRY_URL = "mock://$SCHEMA_SCOPE"
        private const val SERVICE_BOOTSTRAP_SERVERS = "kafka:29092"
    }
}
