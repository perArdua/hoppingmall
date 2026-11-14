package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.internal.InternalInventoryBucketController
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.transaction.PlatformTransactionManager

@DisplayName("버킷 채우기 조건부 등록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class BucketBackfillRegistrationTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(InventoryBucketRepository::class.java, { mock<InventoryBucketRepository>() })
        .withBean(BucketStockService::class.java, { mock<BucketStockService>() })
        .withBean(InventoryCacheEvictor::class.java, { mock<InventoryCacheEvictor>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withUserConfiguration(
            BucketBackfillService::class.java,
            BucketBackfillOnStartup::class.java,
            InternalInventoryBucketController::class.java
        )

    @Test
    fun 모드_설정이_없으면_버킷_채우기를_등록하지_않는다() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(BucketBackfillService::class.java)
            assertThat(context).doesNotHaveBean(BucketBackfillOnStartup::class.java)
            assertThat(context).doesNotHaveBean(InternalInventoryBucketController::class.java)
        }
    }

    @Test
    fun single_모드면_버킷_채우기를_등록하지_않는다() {
        contextRunner.withPropertyValues("inventory.mode=single").run { context ->
            assertThat(context).doesNotHaveBean(BucketBackfillService::class.java)
            assertThat(context).doesNotHaveBean(InternalInventoryBucketController::class.java)
        }
    }

    @Test
    fun bucket_모드면_버킷_채우기와_기동_때_채우기와_내부_입구를_등록한다() {
        contextRunner.withPropertyValues("inventory.mode=bucket").run { context ->
            assertThat(context).hasSingleBean(BucketBackfillService::class.java)
            assertThat(context).hasSingleBean(BucketBackfillOnStartup::class.java)
            assertThat(context).hasSingleBean(InternalInventoryBucketController::class.java)
        }
    }

    @Test
    fun 배치_크기가_0이하면_기동하지_않는다() {
        contextRunner.withPropertyValues("inventory.mode=bucket", "inventory.bucket.backfill.batch-size=0").run { context ->
            assertThat(context).hasFailed()
        }
    }
}
