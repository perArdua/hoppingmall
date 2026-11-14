package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.config.CronTask
import org.springframework.transaction.PlatformTransactionManager

@DisplayName("정산 스냅샷 cron 설정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementSnapshotCronTest {

    @Configuration
    @EnableScheduling
    class SchedulingConfig

    private val contextRunner = ApplicationContextRunner()
        .withBean(SettlementRepository::class.java, { mock<SettlementRepository>() })
        .withBean(SettlementSummaryRepository::class.java, { mock<SettlementSummaryRepository>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withBean(TaskScheduler::class.java, { mock<TaskScheduler>() })
        .withUserConfiguration(SchedulingConfig::class.java, SettlementSnapshotService::class.java)

    @Test
    fun 설정이_없으면_매일_3시에_스냅샷을_만든다() {
        contextRunner.run { context ->
            assertThat(cronExpressions(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java))).containsExactly("0 0 3 * * *")
        }
    }

    @Test
    fun 설정한_cron으로_스냅샷_시각을_바꾼다() {
        contextRunner.withPropertyValues("settlement.snapshot.cron=0 30 4 * * *").run { context ->
            assertThat(cronExpressions(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java))).containsExactly("0 30 4 * * *")
        }
    }

    private fun cronExpressions(processor: ScheduledAnnotationBeanPostProcessor): List<String> =
        processor.scheduledTasks.map { it.task }.filterIsInstance<CronTask>().map { it.expression }
}
