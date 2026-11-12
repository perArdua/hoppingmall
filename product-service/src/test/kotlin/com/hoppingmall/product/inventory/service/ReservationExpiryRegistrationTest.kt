package com.hoppingmall.product.inventory.service

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

@DisplayName("예약 만료 처리기 조건부 등록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationExpiryRegistrationTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(InventoryReservationRepository::class.java, { mock<InventoryReservationRepository>() })
        .withBean(InventoryRepository::class.java, { mock<InventoryRepository>() })
        .withBean(BucketStockService::class.java, { mock<BucketStockService>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
        .withUserConfiguration(ReservationExpiryScheduler::class.java, BucketReservationExpiryJob::class.java)

    @Test
    fun 모드_설정이_없으면_기존_만료_스케줄러만_등록된다() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ReservationExpiryScheduler::class.java)
            assertThat(context).doesNotHaveBean(BucketReservationExpiryJob::class.java)
        }
    }

    @Test
    fun single_모드면_기존_만료_스케줄러만_등록된다() {
        contextRunner.withPropertyValues("inventory.mode=single").run { context ->
            assertThat(context).hasSingleBean(ReservationExpiryScheduler::class.java)
            assertThat(context).doesNotHaveBean(BucketReservationExpiryJob::class.java)
        }
    }

    @Test
    fun bucket_모드면_버킷_만료_잡만_등록된다() {
        contextRunner.withPropertyValues("inventory.mode=bucket").run { context ->
            assertThat(context).hasSingleBean(BucketReservationExpiryJob::class.java)
            assertThat(context).doesNotHaveBean(ReservationExpiryScheduler::class.java)
        }
    }

    @Test
    fun 모드_값의_대소문자가_달라도_한쪽만_등록된다() {
        contextRunner.withPropertyValues("inventory.mode=BUCKET").run { context ->
            assertThat(context).hasSingleBean(BucketReservationExpiryJob::class.java)
            assertThat(context).doesNotHaveBean(ReservationExpiryScheduler::class.java)
        }
        contextRunner.withPropertyValues("inventory.mode=Single").run { context ->
            assertThat(context).hasSingleBean(ReservationExpiryScheduler::class.java)
            assertThat(context).doesNotHaveBean(BucketReservationExpiryJob::class.java)
        }
    }
}
