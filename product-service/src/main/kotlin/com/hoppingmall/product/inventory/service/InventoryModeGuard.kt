package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.InventoryRuntimeMode
import com.hoppingmall.product.inventory.domain.repository.InventoryRuntimeModeStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.LocalDateTime

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
                "모드를 바꾸려면 $PROCEDURE_DOC 의 절차를 따른다"
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
                    "DB=single로 쓰던 재고, 이 인스턴스=${describe(mine)}. single에서 bucket으로 바꾸려면 $PROCEDURE_DOC 의 절차를 따른다"
            }
        } else {
            check(!hasBuckets) {
                "재고 모드 기록이 없는 DB에 inventory_buckets 행이 있어 기동하지 않는다: " +
                    "DB=bucket으로 쓰던 재고, 이 인스턴스=${describe(mine)}. bucket에서 single로 바꾸려면 $PROCEDURE_DOC 의 절차를 따른다"
            }
        }
    }

    private fun isBucket(mode: InventoryRuntimeMode): Boolean = mode.mode.equals(BucketStockService.MODE_BUCKET, ignoreCase = true)

    private fun sameMode(recorded: InventoryRuntimeMode, mine: InventoryRuntimeMode): Boolean =
        recorded.mode.equals(mine.mode, ignoreCase = true) && (!isBucket(mine) || recorded.bucketCount == mine.bucketCount)

    private fun describe(mode: InventoryRuntimeMode): String =
        if (isBucket(mode)) "${mode.mode}(bucket.count=${mode.bucketCount})" else mode.mode

    companion object {
        const val PROCEDURE_DOC = "docs/operations/inventory-mode.md"
    }
}
