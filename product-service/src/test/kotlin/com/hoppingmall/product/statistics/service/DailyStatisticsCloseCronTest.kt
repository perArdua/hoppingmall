package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.port.CartItemQueryPort
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
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("일별 통계 마감 cron 설정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class DailyStatisticsCloseCronTest {

    @Configuration
    @EnableScheduling
    class SchedulingConfig

    private val contextRunner = ApplicationContextRunner()
        .withBean(ProductStatisticsRepository::class.java, { mock<ProductStatisticsRepository>() })
        .withBean(CartItemQueryPort::class.java, { mock<CartItemQueryPort>() })
        .withBean(InventoryRepository::class.java, { mock<InventoryRepository>() })
        .withBean(BucketStockService::class.java, { mock<BucketStockService>() })
        .withBean(ProductStatisticsCommandService::class.java, { mock<ProductStatisticsCommandService>() })
        .withBean(TransactionTemplate::class.java, { mock<TransactionTemplate>() })
        .withBean(TaskScheduler::class.java, { mock<TaskScheduler>() })
        .withUserConfiguration(SchedulingConfig::class.java, ProductStatisticsScheduler::class.java)

    @Test
    fun 설정이_없으면_자정에_마감한다() {
        contextRunner.run { context ->
            assertThat(cronExpressions(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java))).containsExactly("0 0 0 * * *")
        }
    }

    @Test
    fun 설정한_cron으로_마감_시각을_바꾼다() {
        contextRunner.withPropertyValues("product.statistics.daily-close.cron=0 30 1 * * *").run { context ->
            assertThat(cronExpressions(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java))).containsExactly("0 30 1 * * *")
        }
    }

    private fun cronExpressions(processor: ScheduledAnnotationBeanPostProcessor): List<String> =
        processor.scheduledTasks.map { it.task }.filterIsInstance<CronTask>().map { it.expression }
}
