package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.dto.response.InventoryResponse
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InventoryAlreadyExistsException
import com.hoppingmall.product.inventory.exception.InventoryInvalidQuantityException
import com.hoppingmall.product.inventory.exception.InventoryInvalidStockChangeQuantityException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductNotFoundException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import kotlin.random.Random

@Service
@Transactional
class InventoryCommandServiceImpl(
    private val inventoryRepository: InventoryRepository,
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val productRepository: ProductRepository,
    private val bucketStockService: BucketStockService,
    private val reservationGate: InventoryReservationGate,
    private val cacheManagerProvider: ObjectProvider<CacheManager>,
    transactionManager: PlatformTransactionManager
) : InventoryCommandService {

    private val reserveTransaction = TransactionTemplate(transactionManager)

    @Value("\${inventory.reservation.ttl-seconds:600}")
    private var ttlSeconds: Long = 600

    @Value("\${chaos.inventory.failure-rate:0.0}")
    private var chaosFailureRate: Float = 0.0f

    private fun maybeInjectChaos(operation: String) {
        if (chaosFailureRate > 0 && Random.nextFloat() < chaosFailureRate) {
            throw RuntimeException("Chaos injection: inventory $operation failed")
        }
    }

    override fun initStock(request: InventoryInitRequest): InventoryResponse {
        if (!productRepository.existsById(request.productId)) {
            throw ProductNotFoundException()
        }

        if (inventoryRepository.existsByProductId(request.productId)) {
            throw InventoryAlreadyExistsException()
        }

        val inventory = Inventory.create(
            productId = request.productId,
            stockQuantity = request.stockQuantity
        )
        val savedInventory = inventoryRepository.save(inventory)
        if (bucketStockService.enabled) {
            bucketStockService.initBuckets(request.productId, request.stockQuantity)
        }
        return InventoryResponse.from(savedInventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun updateStock(productId: Long, request: InventoryUpdateRequest): InventoryResponse {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.stockQuantity = request.stockQuantity
        if (bucketStockService.enabled) {
            bucketStockService.redistribute(productId, request.stockQuantity)
        }
        return InventoryResponse.from(inventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun decreaseStock(productId: Long, quantity: Int) {
        requirePositiveStockChange(quantity)
        if (bucketStockService.enabled) {
            bucketStockService.decrease(productId, quantity)
            return
        }
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.decreaseStock(quantity)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun increaseStock(productId: Long, quantity: Int) {
        requirePositiveStockChange(quantity)
        restoreStock(productId, quantity)
    }

    private fun requirePositiveStockChange(quantity: Int) {
        if (quantity <= 0) throw InventoryInvalidStockChangeQuantityException()
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun reserveStock(productId: Long, quantity: Int): String {
        requirePositiveQuantity(quantity)
        return withReserveGate(listOf(productId)) {
            inReserveTransaction { lockAll -> reserveOne(productId, quantity, lockAll) }
        }
    }

    override fun confirmReservations(reservationIds: List<String>): Boolean {
        maybeInjectChaos("confirmReservations")
        if (reservationIds.isEmpty()) return true

        val updatedCount = inventoryReservationRepository.batchConfirmByCas(
            reservationIds = reservationIds,
            expectedStatuses = CONFIRMABLE,
            updatedAt = LocalDateTime.now()
        )
        if (updatedCount == reservationIds.size) return true

        val reservations = inventoryReservationRepository.findByReservationIdIn(reservationIds)
        val allConfirmed = reservations.size == reservationIds.size &&
            reservations.all { it.status == ReservationStatus.CONFIRMED }
        if (allConfirmed) return true
        throw ReservationConfirmFailedException()
    }

    override fun cancelReservation(reservationId: String) {
        maybeInjectChaos("cancelReservation")
        cancelLocked(listOf(reservationId))
    }

    override fun cancelReservations(reservationIds: List<String>) {
        if (reservationIds.isEmpty()) return
        cancelLocked(reservationIds)
    }

    override fun cancelConfirmedReservations(reservationIds: List<String>) {
        if (reservationIds.isEmpty()) return
        cancelLockedRows(inventoryReservationRepository.lockConfirmed(reservationIds))
    }

    private fun cancelLocked(reservationIds: List<String>) {
        cancelLockedRows(inventoryReservationRepository.lockCancellable(reservationIds))
    }

    private fun cancelLockedRows(locked: List<Array<Any>>) {
        if (locked.isEmpty()) return

        inventoryReservationRepository.cancelByIds(locked.map { (it[0] as Number).toLong() }, LocalDateTime.now())

        val stockToRestore = locked
            .groupBy { (it[1] as Number).toLong() }
            .mapValues { (_, rows) -> rows.sumOf { (it[2] as Number).toInt() } }
            .toSortedMap()

        evictInventoryCacheAfterCommit(stockToRestore.keys)
        if (bucketStockService.enabled) {
            stockToRestore.forEach { (productId, quantity) -> bucketStockService.restore(productId, quantity) }
            return
        }

        val inventories = inventoryRepository.findByProductIdInForUpdate(stockToRestore.keys.toList())
            .associateBy { it.productId }

        stockToRestore.forEach { (productId, quantity) ->
            inventories[productId]?.increaseStock(quantity)
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun batchReserveStock(items: List<Pair<Long, Int>>): Map<Long, String> {
        items.forEach { (_, quantity) -> requirePositiveQuantity(quantity) }
        return withReserveGate(items.map { it.first }) {
            inReserveTransaction { lockAll -> batchReserveInTransaction(items, lockAll) }
        }
    }

    private fun requirePositiveQuantity(quantity: Int) {
        if (quantity <= 0) throw InventoryInvalidQuantityException()
    }

    private fun batchReserveInTransaction(items: List<Pair<Long, Int>>, lockAll: Boolean): Map<Long, String> {
        val sorted = items.sortedBy { it.first }
        val reservationMap = mutableMapOf<Long, String>()
        try {
            sorted.forEach { (productId, quantity) ->
                val reservationId = reserveOne(productId, quantity, lockAll)
                reservationMap[productId] = reservationId
            }
        } catch (e: Exception) {
            if (!bucketStockService.enabled) {
                reservationMap.values.forEach { cancelReservation(it) }
            }
            throw e
        }
        ReserveDeadline.check()
        evictInventoryCacheAfterCommit(reservationMap.keys)
        return reservationMap
    }

    private fun <T> withReserveGate(productIds: List<Long>, block: () -> T): T =
        if (bucketStockService.enabled) reservationGate.withPermits(productIds, block) else block()

    private fun <T : Any> inReserveTransaction(block: (Boolean) -> T): T {
        try {
            return reserveTransaction.execute { block(false) }!!
        } catch (e: BucketWalkExhaustedException) {
            bucketStockService.recordRestart()
            return reserveTransaction.execute { block(true) }!!
        }
    }

    private fun reserveOne(productId: Long, quantity: Int, lockAll: Boolean): String {
        maybeInjectChaos("reserveStock")
        val deadline = ReserveDeadline.current()
        val reservation = if (deadline != null) {
            InventoryReservation.create(
                productId, quantity,
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(deadline + ReserveDeadline.EXPIRY_GRACE_MS), java.time.ZoneId.systemDefault())
            )
        } else {
            InventoryReservation.create(productId, quantity, ttlSeconds)
        }
        if (bucketStockService.enabled && lockAll) {
            bucketStockService.reserveLockingAll(productId, quantity)
        } else if (bucketStockService.enabled) {
            bucketStockService.reserve(productId, quantity, reservation.reservationId)
        } else {
            val inventory = inventoryRepository.findByProductIdForUpdate(productId)
                ?: throw InventoryNotFoundException()

            inventory.decreaseStock(quantity)
        }

        inventoryReservationRepository.save(reservation)
        return reservation.reservationId
    }

    private fun restoreStock(productId: Long, quantity: Int) {
        if (bucketStockService.enabled) {
            bucketStockService.restore(productId, quantity)
            return
        }
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.increaseStock(quantity)
    }

    private fun evictInventoryCacheAfterCommit(productIds: Collection<Long>) {
        if (productIds.isEmpty()) return
        val cache = cacheManagerProvider.getIfAvailable()?.getCache(INVENTORY_CACHE) ?: return
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            productIds.forEach { cache.evict(it) }
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                productIds.forEach { cache.evict(it) }
            }
        })
    }

    companion object {
        private const val INVENTORY_CACHE = "inventory"
        private val CONFIRMABLE = listOf(ReservationStatus.RESERVED, ReservationStatus.CLAIMED)
    }
}
