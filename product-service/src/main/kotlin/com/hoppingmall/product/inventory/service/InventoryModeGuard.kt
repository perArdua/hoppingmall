package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.InventoryRuntimeMode
import com.hoppingmall.product.inventory.domain.repository.InventoryRuntimeModeStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 기동 중(웹·gRPC·Kafka·스케줄러 시작과 기동 후 버킷 채우기 전) inventory.mode와 bucket.count를
 * inventory_runtime_mode(id=1) 기록과 비교해 다르면 기동을 막는다. 기록이 없으면 첫 인스턴스가 기록한다.
 * 실행 중에도 InventoryModeRecheckJob이 기록을 다시 읽어(기본 5초) 다르면 인스턴스를 내린다.
 * "먼저 모두 내린다"는 이 클래스가 강제하지 못한다. 내리지 않고 기록을 바꾸면 기존 인스턴스가 약 5초 안에 스스로 내려가고, 다시 뜰 때 기동 검사에 막힌다.
 * inventory_runtime_mode 행은 서비스 전체의 kill switch로도 동작한다. 잘못 UPDATE하면 모든 인스턴스가 약 5초 안에 내려가고 고칠 때까지 반복해서 기동에 실패한다.
 *
 * 모드 변경 절차. 모드가 섞인 롤링·카나리 전환은 허용하지 않는다.
 * 1. product-service 인스턴스를 모두 내린다.
 * 2. 재고를 옮기고 기록을 바꾼다.
 *    - single → bucket:
 *      `DELETE FROM inventory_buckets;`
 *      `INSERT INTO inventory_runtime_mode (id, mode, bucket_count, updated_at) VALUES (1, 'bucket', <inventory.bucket.count 값>, NOW(6))
 *      ON DUPLICATE KEY UPDATE mode = 'bucket', bucket_count = <inventory.bucket.count 값>, updated_at = NOW(6);`
 *      버킷은 기동 후 채우기(BucketBackfillOnStartup)가 inventories 재고로 만든다.
 *    - bucket → single: 열린 예약이 있으면 바꾸지 않는다. single 모드는 CLAIMED 예약과 주문에 연결된 RESERVED 예약을
 *      링크 확인 잡처럼 다루지 못한다. 1단계 전에 주문 접수를 막고 아래 값이 0이 될 때까지 기다린다.
 *      `SELECT COUNT(*) FROM inventory_reservations WHERE status IN ('RESERVED', 'CLAIMED');`
 *      그다음 한 트랜잭션으로
 *      `UPDATE inventories i JOIN (SELECT product_id, SUM(stock_quantity) AS total FROM inventory_buckets GROUP BY product_id) b
 *      ON b.product_id = i.product_id SET i.stock_quantity = b.total;`
 *      `DELETE FROM inventory_buckets;`
 *      `UPDATE inventory_runtime_mode SET mode = 'single', bucket_count = NULL, updated_at = NOW(6) WHERE id = 1;`
 *    - 버킷 수 변경(mode는 bucket 그대로): 한 트랜잭션으로
 *      `UPDATE inventories i JOIN (SELECT product_id, SUM(stock_quantity) AS total FROM inventory_buckets GROUP BY product_id) b
 *      ON b.product_id = i.product_id SET i.stock_quantity = b.total;`
 *      `DELETE FROM inventory_buckets;`
 *      `UPDATE inventory_runtime_mode SET mode = 'bucket', bucket_count = <새 inventory.bucket.count 값>, updated_at = NOW(6) WHERE id = 1;`
 *      버킷은 기동 후 채우기가 새 버킷 수로 다시 나눈다.
 * 3. 모든 인스턴스를 같은 inventory.mode와 inventory.bucket.count로 띄운다.
 */
@Component
class InventoryModeGuard(
    private val inventoryRuntimeModeStore: InventoryRuntimeModeStore,
    private val bucketStockService: BucketStockService,
    private val inventoryModeTerminator: InventoryModeTerminator
) : SmartInitializingSingleton {

    private val logger = LoggerFactory.getLogger(InventoryModeGuard::class.java)

    @Volatile
    private var terminating = false

    override fun afterSingletonsInstantiated() {
        val mine = myMode()
        val recorded = inventoryRuntimeModeStore.find() ?: claim(mine)
        check(sameMode(recorded, mine)) {
            "inventory.mode가 DB에 기록된 재고 모드와 달라 기동하지 않는다: DB=${describe(recorded)}, 이 인스턴스=${describe(mine)}. " +
                "모드 변경 절차: $PROCEDURE"
        }
        logger.info("재고 모드 확인: {}", describe(mine))
    }

    fun recheck() {
        if (terminating) return
        val recorded = try {
            inventoryRuntimeModeStore.find()
        } catch (e: DataAccessException) {
            logger.warn("재고 모드 기록을 다시 읽지 못했다: {}", e.message)
            return
        } ?: return
        val mine = myMode()
        if (sameMode(recorded, mine)) return
        terminating = true
        val reason = "실행 중 DB의 재고 모드 기록이 이 인스턴스와 달라져 인스턴스를 내린다: DB=${describe(recorded)}, 이 인스턴스=${describe(mine)}. " +
            "모드 변경 절차: $PROCEDURE"
        logger.error(reason)
        inventoryModeTerminator.terminate(reason)
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
