package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.port.OrderTerminalCheckPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.ScheduledTaskHolder

@DisplayName("확정 예약 정리 등록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ConfirmedReservationCleanupRegistrationTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(JdbcTemplate::class.java, { mock<JdbcTemplate>() })
        .withBean(OrderTerminalCheckPort::class.java, { mock<OrderTerminalCheckPort>() })
        .withUserConfiguration(
            SchedulingTestConfig::class.java,
            ConfirmedReservationCleanupProperties::class.java,
            ConfirmedReservationCleanupJob::class.java
        )

    @Test
    fun 설정이_없으면_기본값으로_매일_04시45분에_60일_지난_예약을_500개씩_묻는다() {
        contextRunner.run { context ->
            val properties = context.getBean(ConfirmedReservationCleanupProperties::class.java)
            assertThat(properties.ageDays).isEqualTo(60)
            assertThat(properties.batchSize).isEqualTo(500)
            assertThat(properties.maxBatches).isEqualTo(100)
            assertThat(cronExpressions(context.getBean(ScheduledTaskHolder::class.java))).containsExactly("0 45 4 * * ?")
        }
    }

    @Test
    fun 프로퍼티로_스케줄_시각과_나이와_배치_크기를_바꾼다() {
        contextRunner.withPropertyValues(
            "inventory.reservation.confirmed-cleanup.cron=0 30 3 * * ?",
            "inventory.reservation.confirmed-cleanup.age-days=90",
            "inventory.reservation.confirmed-cleanup.batch-size=200",
            "inventory.reservation.confirmed-cleanup.max-batches=10"
        ).run { context ->
            val properties = context.getBean(ConfirmedReservationCleanupProperties::class.java)
            assertThat(properties.ageDays).isEqualTo(90)
            assertThat(properties.batchSize).isEqualTo(200)
            assertThat(properties.maxBatches).isEqualTo(10)
            assertThat(cronExpressions(context.getBean(ScheduledTaskHolder::class.java))).containsExactly("0 30 3 * * ?")
        }
    }

    @Test
    fun 배치_크기가_order의_한도_500을_넘으면_기동하지_않는다() {
        contextRunner.withPropertyValues("inventory.reservation.confirmed-cleanup.batch-size=501").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun 배치_크기나_나이나_최대_배치_수가_0이하면_기동하지_않는다() {
        listOf("batch-size=0", "age-days=0", "max-batches=0").forEach { value ->
            contextRunner.withPropertyValues("inventory.reservation.confirmed-cleanup.$value").run { context ->
                assertThat(context).hasFailed()
            }
        }
    }

    private fun cronExpressions(holder: ScheduledTaskHolder): List<String> =
        holder.scheduledTasks.map { it.task }.filterIsInstance<CronTask>().map { it.expression }

    @Configuration
    @EnableScheduling
    @EnableConfigurationProperties
    class SchedulingTestConfig
}
