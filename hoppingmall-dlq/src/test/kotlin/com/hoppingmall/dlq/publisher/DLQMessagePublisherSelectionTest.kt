package com.hoppingmall.dlq.publisher

import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.mock.MockProducerFactory

@DisplayName("DLQ 재발행기 선택")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQMessagePublisherSelectionTest {

    @Test
    fun 서비스가_KafkaTemplate을_직접_등록하면_Kafka_재발행기가_뜬다() {
        ApplicationContextRunner()
            .withUserConfiguration(ServiceKafkaConfig::class.java, PublisherScanConfig::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(DLQMessagePublisher::class.java)
                assertThat(context.getBean(DLQMessagePublisher::class.java))
                    .isInstanceOf(KafkaDLQMessagePublisher::class.java)
            }
    }

    @Test
    fun 자동_설정_KafkaTemplate만_있으면_NoOp_재발행기가_뜬다() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration::class.java))
            .withUserConfiguration(PublisherScanConfig::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KafkaTemplate::class.java)
                assertThat(context).hasSingleBean(DLQMessagePublisher::class.java)
                assertThat(context.getBean(DLQMessagePublisher::class.java))
                    .isInstanceOf(NoOpDLQMessagePublisher::class.java)
            }
    }

    @Configuration
    class ServiceKafkaConfig {
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
        useDefaultFilters = false,
        includeFilters = [
            ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = [KafkaDLQMessagePublisher::class, NoOpDLQMessagePublisher::class]
            )
        ]
    )
    class PublisherScanConfig
}
