package com.hoppingmall.order.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.service.OrderCancellationResultConsumer
import com.hoppingmall.order.order.service.OrderSagaConsumer
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.MicrometerConsumerListener
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer

@DisplayName("Kafka 리스너 동시 소비 수와 소비자 지표 설정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class KafkaListenerConcurrencyTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(
            ListenerConfig::class.java,
            KafkaConfig::class.java,
            OrderSagaConsumer::class.java,
            OrderCancellationResultConsumer::class.java
        )
        .withPropertyValues("spring.kafka.consumer.bootstrap-servers=localhost:1")
        .withBean("noAutoStartup", BeanPostProcessor::class.java, { NoAutoStartup })
        .withBean(DLQCommandService::class.java, { mock<DLQCommandService>() })
        .withBean(EntityManagerFactory::class.java, { mock<EntityManagerFactory>() })
        .withBean(SagaEventLogRepository::class.java, { mock<SagaEventLogRepository>() })
        .withBean(OrderRepository::class.java, { mock<OrderRepository>() })
        .withBean(OrderItemRepository::class.java, { mock<OrderItemRepository>() })
        .withBean(InventoryCommandPort::class.java, { mock<InventoryCommandPort>() })
        .withBean(PaymentQueryPort::class.java, { mock<PaymentQueryPort>() })
        .withBean(TransactionalEventPublisher::class.java, { mock<TransactionalEventPublisher>() })
        .withBean(KafkaEventMapper::class.java, { mock<KafkaEventMapper>() })
        .withBean(SagaCompensationMetrics::class.java, { mock<SagaCompensationMetrics>() })
        .withBean(ObjectMapper::class.java, { ObjectMapper() })
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })

    @Test
    fun 결제_완료_리스너는_따로_정하지_않으면_16개로_소비한다() {
        contextRunner.run { context ->
            assertThat(concurrency(context, KafkaTopics.PAYMENT, OrderConsumerGroups.ORDER_SAGA)).isEqualTo(16)
        }
    }

    @Test
    fun 결제_완료_리스너의_동시_소비_수는_설정으로_바꿀_수_있다() {
        contextRunner.withPropertyValues("order.saga.consumer.concurrency=8").run { context ->
            assertThat(concurrency(context, KafkaTopics.PAYMENT, OrderConsumerGroups.ORDER_SAGA)).isEqualTo(8)
        }
    }

    @Test
    fun 다른_리스너는_공용_팩토리의_기본값_4를_그대로_쓴다() {
        contextRunner.withPropertyValues("order.saga.consumer.concurrency=8").run { context ->
            assertThat(concurrency(context, KafkaTopics.PAYMENT_COMPENSATION, OrderConsumerGroups.ORDER_SAGA)).isEqualTo(4)
            assertThat(concurrency(context, KafkaTopics.PAYMENT_COMPENSATION, OrderConsumerGroups.ORDER_CANCELLATION_RESULT)).isEqualTo(4)
        }
    }

    @Test
    fun 공용_팩토리의_동시_소비_수는_설정으로_바꿀_수_있고_결제_완료_리스너에는_영향이_없다() {
        contextRunner.withPropertyValues("kafka.consumer.concurrency=2").run { context ->
            assertThat(concurrency(context, KafkaTopics.PAYMENT_COMPENSATION, OrderConsumerGroups.ORDER_SAGA)).isEqualTo(2)
            assertThat(concurrency(context, KafkaTopics.PAYMENT_COMPENSATION, OrderConsumerGroups.ORDER_CANCELLATION_RESULT)).isEqualTo(2)
            assertThat(concurrency(context, KafkaTopics.PAYMENT, OrderConsumerGroups.ORDER_SAGA)).isEqualTo(16)
        }
    }

    @Test
    fun 리스너용_소비자는_Kafka_클라이언트_지표를_미터_레지스트리에_등록한다() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            val listeners = context.getBean(ConsumerFactory::class.java).listeners
            assertThat(listeners).hasSize(1)
            assertThat(listeners.single()).isInstanceOf(MicrometerConsumerListener::class.java)
        }
    }

    @Test
    fun 소비자를_만들면_파티션_지연_지표가_생긴다() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            context.getBean(ConsumerFactory::class.java).createConsumer().use {
                val meterRegistry = context.getBean(MeterRegistry::class.java)
                assertThat(meterRegistry.find("kafka.consumer.fetch.manager.records.lag.max").meters()).isNotEmpty()
            }
        }
    }

    private fun concurrency(context: AssertableApplicationContext, topic: String, groupId: String): Int {
        assertThat(context).hasNotFailed()
        val containers = context.getBean(KafkaListenerEndpointRegistry::class.java).listenerContainers
            .filterIsInstance<ConcurrentMessageListenerContainer<*, *>>()
            .filter { it.containerProperties.topics?.contains(topic) == true && it.groupId == groupId }
        assertThat(containers).hasSize(1)
        return containers.single().concurrency
    }

    @Configuration(proxyBeanMethods = false)
    @EnableKafka
    class ListenerConfig

    private object NoAutoStartup : BeanPostProcessor {
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
            if (bean is ConcurrentKafkaListenerContainerFactory<*, *>) {
                bean.setAutoStartup(false)
            }
            return bean
        }
    }
}
