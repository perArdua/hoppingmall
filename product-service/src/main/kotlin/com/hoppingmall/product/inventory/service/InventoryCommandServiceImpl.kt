package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.domain.repository.KeyedReservationInserter
import com.hoppingmall.product.inventory.dto.request.InventoryAdjustRequest
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.dto.response.InventoryResponse
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InvalidReservationRequestKeyException
import com.hoppingmall.product.inventory.exception.InventoryAlreadyExistsException
import com.hoppingmall.product.inventory.exception.InventoryBusyException
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.inventory.exception.InventoryInvalidQuantityException
import com.hoppingmall.product.inventory.exception.InventoryInvalidStockAdjustmentException
import com.hoppingmall.product.inventory.exception.InventoryInvalidStockChangeQuantityException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.inventory.exception.InventoryStockChangedException
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.inventory.exception.ReservationRequestKeyConflictException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductNotFoundException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
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
    private val keyedReservationInserter: KeyedReservationInserter,
    private val productRepository: ProductRepository,
    private val bucketStockService: BucketStockService,
    private val reservationGate: InventoryReservationGate,
    private val cacheManagerProvider: ObjectProvider<CacheManager>,
    meterRegistry: MeterRegistry,
    transactionManager: PlatformTransactionManager
) : InventoryCommandService {

    private val log = LoggerFactory.getLogger(javaClass)

    private val reserveTransaction = TransactionTemplate(transactionManager)

    private val keyConflictReplayCounter = Counter.builder("inventory.reserve.replayed")
        .tag("reason", "key_conflict")
        .description("Reserves that lost a request key conflict and returned the committed reservations")
        .register(meterRegistry)

    private val busyReplayCounter = Counter.builder("inventory.reserve.replayed")
        .tag("reason", "busy")
        .description("Reserves refused a permit that returned the same request key's in-progress reservations")
        .register(meterRegistry)

    private val deadlockRetryCounter = Counter.builder("inventory.reserve.retried")
        .tag("reason", "deadlock")
        .description("Request key reserves rolled back by an InnoDB deadlock and run again")
        .register(meterRegistry)

    @Autowired(required = false)
    var asyncCacheEvictor: InventoryCacheAsyncEvictor? = null

    @Value("\${inventory.reservation.ttl-seconds:600}")
    private var ttlSeconds: Long = 600

    @Value("\${inventory.semaphore.same-key-wait-ms:2000}")
    private var sameKeyWaitMs: Long = 2000

    @Value("\${chaos.inventory.failure-rate:0.0}")
    private var chaosFailureRate: Float = 0.0f

    private fun maybeInjectChaos(operation: String) {
        if (chaosFailureRate > 0 && Random.nextFloat() < chaosFailureRate) {
            throw RuntimeException("Chaos injection: inventory $operation failed")
        }
    }

    override fun initStock(request: InventoryInitRequest): InventoryResponse {
        val productId = requireNotNull(request.productId)
        val stockQuantity = requireNotNull(request.stockQuantity)
        if (!productRepository.existsById(productId)) {
            throw ProductNotFoundException()
        }

        if (inventoryRepository.existsByProductId(productId)) {
            throw InventoryAlreadyExistsException()
        }

        val inventory = Inventory.create(
            productId = productId,
            stockQuantity = stockQuantity
        )
        val savedInventory = inventoryRepository.save(inventory)
        if (bucketStockService.enabled) {
            bucketStockService.initBuckets(productId, stockQuantity)
        }
        return InventoryResponse.from(savedInventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun updateStock(productId: Long, request: InventoryUpdateRequest): InventoryResponse {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?.takeIf { it.deletedAt == null }
            ?: throw InventoryNotFoundException()

        val stockQuantity = requireNotNull(request.stockQuantity)
        val expectedStockQuantity = requireNotNull(request.expectedStockQuantity)
        if (bucketStockService.enabled) {
            if (!bucketStockService.replaceTotal(productId, expectedStockQuantity, stockQuantity)) {
                throw InventoryStockChangedException()
            }
        } else if (inventory.stockQuantity != expectedStockQuantity) {
            throw InventoryStockChangedException()
        }
        inventory.stockQuantity = stockQuantity
        return InventoryResponse.from(inventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun adjustStock(productId: Long, request: InventoryAdjustRequest): InventoryResponse {
        val delta = requireNotNull(request.delta)
        if (delta == 0) throw InventoryInvalidStockAdjustmentException()
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?.takeIf { it.deletedAt == null }
            ?: throw InventoryNotFoundException()

        if (bucketStockService.enabled) {
            if (delta > 0) bucketStockService.restore(productId, delta) else bucketStockService.decrease(productId, -delta)
            return InventoryResponse.from(inventory).copy(stockQuantity = bucketStockService.totalStock(productId))
        }
        if (delta > 0) inventory.increaseStock(delta) else inventory.decreaseStock(-delta)
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

        val ids = inventoryReservationRepository.findIdsByReservationIdIn(reservationIds)
        val updatedCount = if (ids.isEmpty()) 0 else inventoryReservationRepository.batchConfirmByCas(
            ids = ids,
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
        val ids = inventoryReservationRepository.findIdsByReservationIdIn(reservationIds)
        if (ids.isEmpty()) return
        cancelLockedRows(inventoryReservationRepository.lockConfirmed(ids))
    }

    private fun cancelLocked(reservationIds: List<String>) {
        val ids = inventoryReservationRepository.findIdsByReservationIdIn(reservationIds)
        if (ids.isEmpty()) return
        cancelLockedRows(inventoryReservationRepository.lockCancellable(ids))
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun batchReserveStock(items: List<Pair<Long, Int>>, requestKey: String): Map<Long, String> {
        if (requestKey.isBlank() || requestKey.length > InventoryReservation.REQUEST_KEY_MAX_LENGTH) {
            throw InvalidReservationRequestKeyException()
        }
        items.forEach { (_, quantity) -> requirePositiveQuantity(quantity) }
        return try {
            withReserveGate(items.map { it.first }, requestKey) {
                try {
                    retryingDeadlock(requestKey) { inReserveTransaction { lockAll -> replayOrReserve(requestKey, items, lockAll) } }
                } catch (e: DataIntegrityViolationException) {
                    replayAfterViolation(requestKey, items, e)
                } catch (e: InventoryInsufficientStockException) {
                    reserveTransaction.execute { findInProgress(requestKey, items, "재고 부족") } ?: throw e
                }
            }
        } catch (e: InventoryBusyException) {
            replayAfterBusy(requestKey, items) ?: throw e
        }
    }

    private fun replayAfterBusy(requestKey: String, items: List<Pair<Long, Int>>): Map<Long, String>? {
        reservationGate.awaitInFlight(requestKey, busyReplayWaitMs())
        val replayed = reserveTransaction.execute { findInProgress(requestKey, items, "허가 초과") } ?: return null
        busyReplayCounter.increment()
        return replayed
    }

    private fun busyReplayWaitMs(now: Long = System.currentTimeMillis()): Long {
        val deadline = ReserveDeadline.current() ?: return sameKeyWaitMs
        return minOf(sameKeyWaitMs, deadline - ReserveDeadline.MARGIN_MS - now)
    }

    private fun <T> retryingDeadlock(requestKey: String, block: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: PessimisticLockingFailureException) {
                if (!KeyedReservationInserter.isDeadlock(e) || attempt >= DEADLOCK_ATTEMPTS) throw e
                deadlockRetryCounter.increment()
                log.info("요청 키 예약이 교착으로 롤백되어 다시 함: requestKey={}, attempt={}", requestKey, attempt)
                ReserveDeadline.check()
                attempt++
            }
        }
    }

    private fun replayOrReserve(requestKey: String, items: List<Pair<Long, Int>>, lockAll: Boolean): Map<Long, String> {
        val existing = inventoryReservationRepository.findByRequestKey(requestKey)
        if (existing.isEmpty()) return batchReserveInTransaction(items, lockAll, requestKey)
        if (existing.all { it.status in CLOSED }) {
            inventoryReservationRepository.releaseRequestKey(existing.mapNotNull { it.id }, requestKey, LocalDateTime.now())
            return batchReserveInTransaction(items, lockAll, requestKey)
        }
        return replay(existing, items)
    }

    private fun replayAfterViolation(
        requestKey: String,
        items: List<Pair<Long, Int>>,
        violation: DataIntegrityViolationException
    ): Map<Long, String> {
        val keyConflict = KeyedReservationInserter.isRequestKeyConflict(violation)
        val replayed = try {
            reserveTransaction.execute { findReplayable(requestKey, items) } ?: throw violation
        } catch (e: Exception) {
            if (keyConflict) log.error("요청 키 충돌이 재생으로 끝나지 않음: {}", violation.mostSpecificCause.message)
            throw e
        }
        if (keyConflict) {
            keyConflictReplayCounter.increment()
            log.info("요청 키 충돌 뒤 먼저 커밋된 예약을 돌려줌: requestKey={}, count={}", requestKey, replayed.size)
        }
        return replayed
    }

    private fun findReplayable(requestKey: String, items: List<Pair<Long, Int>>): Map<Long, String>? {
        val existing = inventoryReservationRepository.findByRequestKey(requestKey)
        if (existing.isEmpty()) return null
        return replay(existing, items)
    }

    private fun findInProgress(requestKey: String, items: List<Pair<Long, Int>>, reason: String): Map<Long, String>? {
        val existing = inventoryReservationRepository.findByRequestKey(requestKey)
        if (existing.isEmpty() || existing.any { it.status !in REPLAYABLE }) return null
        val replayed = replay(existing, items)
        log.info("{} 뒤 같은 요청 키의 진행 중 예약을 돌려줌: requestKey={}, count={}", reason, requestKey, replayed.size)
        return replayed
    }

    private fun replay(existing: List<InventoryReservation>, items: List<Pair<Long, Int>>): Map<Long, String> {
        val requested = items.toMap()
        val sameRequest = existing.size == items.size && existing.all { requested[it.productId] == it.quantity }
        if (!sameRequest || existing.any { it.status !in REPLAYABLE }) throw ReservationRequestKeyConflictException()
        return existing.associate { it.productId to it.reservationId }
    }

    private fun requirePositiveQuantity(quantity: Int) {
        if (quantity <= 0) throw InventoryInvalidQuantityException()
    }

    private fun batchReserveInTransaction(
        items: List<Pair<Long, Int>>,
        lockAll: Boolean,
        requestKey: String? = null
    ): Map<Long, String> {
        val sorted = items.sortedBy { it.first }
        val reservationMap = mutableMapOf<Long, String>()
        try {
            sorted.forEach { (productId, quantity) ->
                val reservationId = reserveOne(productId, quantity, lockAll, requestKey)
                reservationMap[productId] = reservationId
            }
        } catch (e: Exception) {
            if (!bucketStockService.enabled && e !is DataIntegrityViolationException) {
                reservationMap.values.forEach { cancelReservation(it) }
            }
            throw e
        }
        ReserveDeadline.check()
        evictInventoryCacheAfterCommit(reservationMap.keys, asyncCacheEvictor)
        return reservationMap
    }

    private fun <T> withReserveGate(productIds: List<Long>, block: () -> T): T =
        if (bucketStockService.enabled) reservationGate.withPermits(productIds, block) else block()

    private fun <T> withReserveGate(productIds: List<Long>, requestKey: String, block: () -> T): T =
        if (bucketStockService.enabled) reservationGate.withPermits(productIds, requestKey, block) else block()

    private fun <T : Any> inReserveTransaction(block: (Boolean) -> T): T {
        try {
            return reserveTransaction.execute { block(false) }!!
        } catch (e: BucketWalkExhaustedException) {
            bucketStockService.recordRestart()
            return reserveTransaction.execute { block(true) }!!
        }
    }

    private fun reserveOne(productId: Long, quantity: Int, lockAll: Boolean, requestKey: String? = null): String {
        maybeInjectChaos("reserveStock")
        val deadline = ReserveDeadline.current()
        val reservation = if (deadline != null) {
            InventoryReservation.create(
                productId, quantity,
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(deadline + ReserveDeadline.EXPIRY_GRACE_MS), java.time.ZoneId.systemDefault()),
                requestKey = requestKey
            )
        } else {
            InventoryReservation.create(productId, quantity, ttlSeconds, requestKey)
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

        if (requestKey != null) {
            keyedReservationInserter.insert(reservation)
        } else {
            inventoryReservationRepository.save(reservation)
        }
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

    private fun evictInventoryCacheAfterCommit(productIds: Collection<Long>, asyncEvictor: InventoryCacheAsyncEvictor? = null) {
        if (productIds.isEmpty()) return
        val cache = cacheManagerProvider.getIfAvailable()?.getCache(INVENTORY_CACHE) ?: return
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            productIds.forEach { cache.evict(it) }
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                if (asyncEvictor != null) asyncEvictor.submit(productIds) else productIds.forEach { cache.evict(it) }
            }
        })
    }

    companion object {
        private const val INVENTORY_CACHE = "inventory"
        private val CONFIRMABLE = listOf(ReservationStatus.RESERVED, ReservationStatus.CLAIMED)
        private val REPLAYABLE = setOf(ReservationStatus.RESERVED, ReservationStatus.CLAIMED)
        private val CLOSED = setOf(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, ReservationStatus.EXPIRED)
        private const val DEADLOCK_ATTEMPTS = 3
    }
}
