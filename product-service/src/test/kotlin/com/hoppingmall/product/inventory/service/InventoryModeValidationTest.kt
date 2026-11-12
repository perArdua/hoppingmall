package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.transaction.PlatformTransactionManager

@DisplayName("재고 모드 설정 값 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryModeValidationTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(InventoryReservationRepository::class.java, { mock<InventoryReservationRepository>() })
        .withBean(InventoryRepository::class.java, { mock<InventoryRepository>() })
        .withBean(InventoryBucketRepository::class.java, { mock<InventoryBucketRepository>() })
        .withBean(InventoryBucketLocker::class.java, { mock<InventoryBucketLocker>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
        .withUserConfiguration(
            BucketStockService::class.java,
            ReservationExpiryScheduler::class.java,
            BucketReservationExpiryJob::class.java
        )

    @Test
    fun single과_bucket이_아닌_모드_값이면_기동이_실패한다() {
        listOf("buckets", "singel", "").forEach { mode ->
            contextRunner.withPropertyValues("inventory.mode=$mode").run { context ->
                assertThat(context).describedAs("inventory.mode=%s", mode).hasFailed()
                assertThat(context.startupFailure).rootCause().hasMessageContaining("inventory.mode")
            }
        }
    }

    @Test
    fun 허용된_모드_값이면_대소문자와_상관없이_만료_처리기_하나와_함께_뜬다() {
        listOf("single", "Single", "bucket", "BUCKET").forEach { mode ->
            contextRunner.withPropertyValues("inventory.mode=$mode").run { context ->
                assertThat(context).describedAs("inventory.mode=%s", mode).hasNotFailed()
                val expiryHandlers = context.getBeansOfType(ReservationExpiryScheduler::class.java).size +
                    context.getBeansOfType(BucketReservationExpiryJob::class.java).size
                assertThat(expiryHandlers).describedAs("inventory.mode=%s", mode).isEqualTo(1)
            }
        }
    }

    @Test
    fun 모드_설정이_없으면_single로_뜬다() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(BucketStockService::class.java).enabled).isFalse()
            assertThat(context).hasSingleBean(ReservationExpiryScheduler::class.java)
        }
    }
}
