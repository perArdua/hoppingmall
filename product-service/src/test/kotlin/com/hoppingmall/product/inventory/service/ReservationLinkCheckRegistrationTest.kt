package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.transaction.PlatformTransactionManager

@DisplayName("예약 링크 확인 잡 조건부 등록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationLinkCheckRegistrationTest {

    private fun contextRunner(bucketMode: Boolean) = ApplicationContextRunner()
        .withBean(InventoryReservationRepository::class.java, { mock<InventoryReservationRepository>() })
        .withBean(BucketStockService::class.java, {
            mock<BucketStockService>().also { whenever(it.enabled).thenReturn(bucketMode) }
        })
        .withBean(ReservationLinkPort::class.java, { mock<ReservationLinkPort>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
        .withUserConfiguration(ReservationLinkCheckJob::class.java, BucketReservationExpiryJob::class.java)

    @Test
    fun 링크_확인_설정이_없으면_링크_확인_잡은_등록되지_않고_버킷_만료_잡만_등록된다() {
        contextRunner(bucketMode = true).withPropertyValues("inventory.mode=bucket").run { context ->
            assertThat(context).doesNotHaveBean(ReservationLinkCheckJob::class.java)
            assertThat(context).hasSingleBean(BucketReservationExpiryJob::class.java)
        }
    }

    @Test
    fun 링크_확인이_꺼져_있으면_링크_확인_잡은_등록되지_않는다() {
        contextRunner(bucketMode = true)
            .withPropertyValues("inventory.mode=bucket", "inventory.link-check.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(ReservationLinkCheckJob::class.java)
                assertThat(context).hasSingleBean(BucketReservationExpiryJob::class.java)
            }
    }

    @Test
    fun bucket_모드에서_링크_확인을_켜면_링크_확인_잡이_등록된다() {
        contextRunner(bucketMode = true)
            .withPropertyValues("inventory.mode=bucket", "inventory.link-check.enabled=true")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(ReservationLinkCheckJob::class.java)
                assertThat(context).hasSingleBean(BucketReservationExpiryJob::class.java)
            }
    }

    @Test
    fun single_모드에서_링크_확인을_켜면_기동이_실패한다() {
        contextRunner(bucketMode = false)
            .withPropertyValues("inventory.mode=single", "inventory.link-check.enabled=true")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasRootCauseInstanceOf(IllegalArgumentException::class.java)
                    .hasRootCauseMessage("inventory.link-check.enabled requires inventory.mode=bucket")
            }
    }
}
