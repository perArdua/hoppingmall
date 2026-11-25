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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource

@DisplayName("outbox 발행기 플래그 기본값")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OutboxPublishDefaultsTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(OutboxEventRepository::class.java, { mock<OutboxEventRepository>() })
        .withBean(KafkaTemplate::class.java, { mock<KafkaTemplate<String, Any>>() })
        .withBean(OutboxMetrics::class.java, { mock<OutboxMetrics>() })
        .withBean(AvroEventConverter::class.java, { mock<AvroEventConverter>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withBean(OutboxBrokerBackoff::class.java)
        .withBean(OutboxEventPublisher::class.java)
        .withBean(OutboxPublishWaker::class.java)

    @Test
    fun 설정이_없으면_MySQL에서_일괄_선점과_커밋_뒤_깨우기가_켜진다() {
        contextRunner.withBean(DataSource::class.java, { dataSource("MySQL") }).run { context ->
            assertThat(ReflectionTestUtils.getField(context.getBean(OutboxEventPublisher::class.java), "batchClaim"))
                .isEqualTo(true)
            assertThat(ReflectionTestUtils.getField(context.getBean(OutboxPublishWaker::class.java), "enabled"))
                .isEqualTo(true)
        }
    }

    @Test
    fun 설정이_없어도_MySQL이_아닌_DB에서는_일괄_선점이_꺼지고_커밋_뒤_깨우기는_켜진다() {
        contextRunner.withBean(DataSource::class.java, { dataSource("H2") }).run { context ->
            assertThat(ReflectionTestUtils.getField(context.getBean(OutboxEventPublisher::class.java), "batchClaim"))
                .isEqualTo(false)
            assertThat(ReflectionTestUtils.getField(context.getBean(OutboxPublishWaker::class.java), "enabled"))
                .isEqualTo(true)
        }
    }

    @Test
    fun DataSource가_없으면_일괄_선점이_꺼진다() {
        contextRunner.run { context ->
            assertThat(ReflectionTestUtils.getField(context.getBean(OutboxEventPublisher::class.java), "batchClaim"))
                .isEqualTo(false)
        }
    }

    @Test
    fun 설정으로_일괄_선점과_커밋_뒤_깨우기를_끌_수_있다() {
        val mySql = dataSource("MySQL")
        contextRunner
            .withBean(DataSource::class.java, { mySql })
            .withPropertyValues("outbox.publish.batch-claim=false", "outbox.publish.wake-on-commit=false")
            .run { context ->
                assertThat(ReflectionTestUtils.getField(context.getBean(OutboxEventPublisher::class.java), "batchClaim"))
                    .isEqualTo(false)
                assertThat(ReflectionTestUtils.getField(context.getBean(OutboxPublishWaker::class.java), "enabled"))
                    .isEqualTo(false)
                verify(mySql, never()).connection
            }
    }

    private fun dataSource(productName: String): DataSource {
        val metaData = mock<DatabaseMetaData>()
        whenever(metaData.databaseProductName).thenReturn(productName)
        val connection = mock<Connection>()
        whenever(connection.metaData).thenReturn(metaData)
        val dataSource = mock<DataSource>()
        whenever(dataSource.connection).thenReturn(connection)
        return dataSource
    }
}
