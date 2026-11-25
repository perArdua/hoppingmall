package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.InventoryRuntimeMode
import com.hoppingmall.product.inventory.domain.repository.InventoryRuntimeModeStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 기동 중(웹·gRPC·Kafka·스케줄러 시작과 기동 후 버킷 채우기 전) inventory.mode와 bucket.count를
 * inventory_runtime_mode(id=1) 기록과 비교해 다르면 기동을 막는다. 기록이 없으면 첫 인스턴스가 기록한다.
 *
 * 모드 변경 절차. 모드가 섞인 롤링·카나리 전환은 허용하지 않는다.
 * 1. product-service 인스턴스를 모두 내린다.
 * 2. 재고를 옮기고 기록을 바꾼다.
 *    - single → bucket: `DELETE FROM inventory_buckets;`
 *      `INSERT INTO inventory_runtime_mode (id, mode, bucket_count, updated_at) VALUES (1, 'bucket', 8, NOW(6))
 *      ON DUPLICATE KEY UPDATE mode = 'bucket', bucket_count = 8, updated_at = NOW(6);`
 *      버킷은 기동 후 채우기(BucketBackfillOnStartup)가 inventories 재고로 만든다.
 *    - bucket → single (한 트랜잭션):
 *      `UPDATE inventories i JOIN (SELECT product_id, SUM(stock_quantity) AS total FROM inventory_buckets GROUP BY product_id) b
 *      ON b.product_id = i.product_id SET i.stock_quantity = b.total;`
 *      `DELETE FROM inventory_buckets;`
 *      `UPDATE inventory_runtime_mode SET mode = 'single', bucket_count = NULL, updated_at = NOW(6) WHERE id = 1;`
 *    - 버킷 수 변경: bucket → single의 앞 두 문장 뒤 `UPDATE inventory_runtime_mode SET bucket_count = 새 값 ... WHERE id = 1;`
 * 3. 모든 인스턴스를 같은 inventory.mode와 inventory.bucket.count로 띄운다.
 */
@Component
class InventoryModeGuard(
    private val inventoryRuntimeModeStore: InventoryRuntimeModeStore,
    private val bucketStockService: BucketStockService
) : SmartInitializingSingleton {

    private val logger = LoggerFactory.getLogger(InventoryModeGuard::class.java)

    override fun afterSingletonsInstantiated() {
        val mine = myMode()
        val recorded = inventoryRuntimeModeStore.find() ?: claim(mine)
        check(sameMode(recorded, mine)) {
            "inventory.mode가 DB에 기록된 재고 모드와 달라 기동하지 않는다: DB=${describe(recorded)}, 이 인스턴스=${describe(mine)}. " +
                "모드 변경 절차: $PROCEDURE"
        }
        logger.info("재고 모드 확인: {}", describe(mine))
    }

    private fun myMode(): InventoryRuntimeMode =
        if (bucketStockService.enabled) {
            InventoryRuntimeMode(InventoryRuntimeMode.SINGLETON_ID, BucketStockService.MODE_BUCKET, bucketStockService.bucketCount, LocalDateTime.now())
        } else {
            InventoryRuntimeMode(InventoryRuntimeMode.SINGLETON_ID, BucketStockService.MODE_SINGLE, null, LocalDateTime.now())
        }

    private fun claim(mine: InventoryRuntimeMode): InventoryRuntimeMode {
        checkFirstBoot(mine)
        return try {
            inventoryRuntimeModeStore.insert(mine)
            mine
        } catch (e: DuplicateKeyException) {
            inventoryRuntimeModeStore.find() ?: throw e
        }
    }

    private fun checkFirstBoot(mine: InventoryRuntimeMode) {
        val hasBuckets = inventoryRuntimeModeStore.hasBuckets()
        if (isBucket(mine)) {
            check(hasBuckets || !inventoryRuntimeModeStore.hasInventoryStock()) {
                "재고 모드 기록이 없는 DB에 inventory_buckets는 비어 있고 inventories에만 재고가 있어 기동하지 않는다: " +
                    "DB=single로 쓰던 재고, 이 인스턴스=${describe(mine)}. single에서 bucket으로 바꾸는 절차: $PROCEDURE"
            }
        } else {
            check(!hasBuckets) {
                "재고 모드 기록이 없는 DB에 inventory_buckets 행이 있어 기동하지 않는다: " +
                    "DB=bucket으로 쓰던 재고, 이 인스턴스=${describe(mine)}. bucket에서 single로 바꾸는 절차: $PROCEDURE"
            }
        }
    }

    private fun isBucket(mode: InventoryRuntimeMode): Boolean = mode.mode.equals(BucketStockService.MODE_BUCKET, ignoreCase = true)

    private fun sameMode(recorded: InventoryRuntimeMode, mine: InventoryRuntimeMode): Boolean =
        recorded.mode.equals(mine.mode, ignoreCase = true) && (!isBucket(mine) || recorded.bucketCount == mine.bucketCount)

    private fun describe(mode: InventoryRuntimeMode): String =
        if (isBucket(mode)) "${mode.mode}(bucket.count=${mode.bucketCount})" else mode.mode

    companion object {
        const val PROCEDURE = "InventoryModeGuard 클래스 주석"
    }
}
