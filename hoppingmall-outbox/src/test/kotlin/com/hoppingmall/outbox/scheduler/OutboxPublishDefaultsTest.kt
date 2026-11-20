package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager

@DisplayName("outbox 발행기 플래그 기본값")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OutboxPublishDefaultsTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(OutboxEventRepository::class.java, { mock<OutboxEventRepository>() })
        .withBean(KafkaTemplate::class.java, { mock<KafkaTemplate<String, Any>>() })
        .withBean(OutboxMetrics::class.java, { mock<OutboxMetrics>() })
        .withBean(AvroEventConverter::class.java, { mock<AvroEventConverter>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withBean(OutboxEventPublisher::class.java)
        .withBean(OutboxPublishWaker::class.java)

    @Test
    fun 설정이_없으면_일괄_선점과_커밋_뒤_깨우기가_켜진다() {
        contextRunner.run { context ->
            assertThat(ReflectionTestUtils.getField(context.getBean(OutboxEventPublisher::class.java), "batchClaim"))
                .isEqualTo(true)
            assertThat(ReflectionTestUtils.getField(context.getBean(OutboxPublishWaker::class.java), "enabled"))
                .isEqualTo(true)
        }
    }

    @Test
    fun 설정으로_일괄_선점과_커밋_뒤_깨우기를_끌_수_있다() {
        contextRunner
            .withPropertyValues("outbox.publish.batch-claim=false", "outbox.publish.wake-on-commit=false")
            .run { context ->
                assertThat(ReflectionTestUtils.getField(context.getBean(OutboxEventPublisher::class.java), "batchClaim"))
                    .isEqualTo(false)
                assertThat(ReflectionTestUtils.getField(context.getBean(OutboxPublishWaker::class.java), "enabled"))
                    .isEqualTo(false)
            }
    }
}
