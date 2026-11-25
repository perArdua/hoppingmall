package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryRuntimeMode
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryRuntimeModeStore
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("재고 모드 기록과 기동 검사 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryModeGuardMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryRuntimeModeStore: InventoryRuntimeModeStore

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var inventoryBucketRepository: InventoryBucketRepository

    @Autowired
    private lateinit var inventoryBucketLocker: InventoryBucketLocker

    @BeforeEach
    fun clearRuntimeMode() {
        jdbcTemplate.update("DELETE FROM inventory_runtime_mode")
    }

    @Test
    fun 기록한_모드와_버킷_수를_그대로_읽는다() {
        val updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        inventoryRuntimeModeStore.insert(InventoryRuntimeMode(InventoryRuntimeMode.SINGLETON_ID, "bucket", 8, updatedAt))

        val found = inventoryRuntimeModeStore.find()!!

        assertThat(found.id).isEqualTo(InventoryRuntimeMode.SINGLETON_ID)
        assertThat(found.mode).isEqualTo("bucket")
        assertThat(found.bucketCount).isEqualTo(8)
        assertThat(found.updatedAt).isEqualTo(updatedAt)
    }

    @Test
    fun single_기록의_버킷_수는_비어_있다() {
        inventoryRuntimeModeStore.insert(InventoryRuntimeMode(InventoryRuntimeMode.SINGLETON_ID, "single", null, LocalDateTime.now()))

        assertThat(inventoryRuntimeModeStore.find()!!.bucketCount).isNull()
    }

    @Test
    fun 이미_기록이_있으면_다시_기록하지_못하고_먼저_기록한_값이_남는다() {
        inventoryRuntimeModeStore.insert(InventoryRuntimeMode(InventoryRuntimeMode.SINGLETON_ID, "bucket", 8, LocalDateTime.now()))

        assertThatThrownBy {
            inventoryRuntimeModeStore.insert(InventoryRuntimeMode(InventoryRuntimeMode.SINGLETON_ID, "single", null, LocalDateTime.now()))
        }.isInstanceOf(DuplicateKeyException::class.java)

        assertThat(inventoryRuntimeModeStore.find()!!.mode).isEqualTo("bucket")
    }

    @Test
    fun 버킷_행과_남은_재고가_있는지_읽는다() {
        assertThat(inventoryRuntimeModeStore.hasBuckets()).isFalse()
        assertThat(inventoryRuntimeModeStore.hasInventoryStock()).isFalse()

        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 0))
        assertThat(inventoryRuntimeModeStore.hasInventoryStock()).isFalse()

        jdbcTemplate.update("UPDATE inventories SET stock_quantity = 5 WHERE product_id = ?", PRODUCT_ID)
        jdbcTemplate.update("INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, 0, 5)", PRODUCT_ID)

        assertThat(inventoryRuntimeModeStore.hasInventoryStock()).isTrue()
        assertThat(inventoryRuntimeModeStore.hasBuckets()).isTrue()
    }

    @Test
    fun 다른_모드의_두_인스턴스가_빈_DB에서_동시에_뜨면_하나만_기록하고_다른_하나는_기동을_막는다() {
        val guards = mapOf("single" to guard("single"), "bucket" to guard("bucket"))
        val ready = CountDownLatch(guards.size)
        val start = CountDownLatch(1)

        val results = guards.mapValues { (_, guard) ->
            executor.submit<Result<Unit>> {
                ready.countDown()
                start.await()
                runCatching { guard.afterSingletonsInstantiated() }
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        start.countDown()
        val outcomes = results.mapValues { it.value.get(30, TimeUnit.SECONDS) }

        val started = outcomes.filterValues { it.isSuccess }.keys
        val refused = outcomes.filterValues { it.isFailure }
        assertThat(started).hasSize(1)
        assertThat(refused).hasSize(1)
        assertThat(refused.values.single().exceptionOrNull())
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(InventoryModeGuard.PROCEDURE)
        assertThat(inventoryRuntimeModeStore.find()!!.mode).isEqualTo(started.single())
    }

    private fun guard(mode: String) = InventoryModeGuard(
        inventoryRuntimeModeStore,
        BucketStockService(inventoryBucketRepository, inventoryBucketLocker, SimpleMeterRegistry(), mode, 8)
    )

    companion object {
        private const val PRODUCT_ID = 9461L
    }
}
