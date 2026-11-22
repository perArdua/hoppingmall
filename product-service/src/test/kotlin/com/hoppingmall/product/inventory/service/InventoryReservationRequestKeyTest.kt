package com.hoppingmall.product.inventory.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.domain.repository.KeyedReservationInserter
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InvalidReservationRequestKeyException
import com.hoppingmall.product.inventory.exception.ReservationRequestKeyConflictException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.CacheManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.sql.SQLException

@DisplayName("InventoryCommandServiceImpl 예약 요청 키")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InventoryReservationRequestKeyTest {

    @Mock
    private lateinit var inventoryRepository: InventoryRepository

    @Mock
    private lateinit var inventoryReservationRepository: InventoryReservationRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var bucketStockService: BucketStockService

    @Mock
    private lateinit var reservationGate: InventoryReservationGate

    @Mock
    private lateinit var cacheManagerProvider: ObjectProvider<CacheManager>

    @Mock
    private lateinit var keyedReservationInserter: KeyedReservationInserter

    @Spy
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry()

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @InjectMocks
    private lateinit var service: InventoryCommandServiceImpl

    private val logs = ListAppender<ILoggingEvent>()
    private val serviceLogger = LoggerFactory.getLogger(InventoryCommandServiceImpl::class.java) as Logger

    @AfterEach
    fun tearDown() {
        serviceLogger.detachAppender(logs)
    }

    @BeforeEach
    fun setUp() {
        logs.start()
        serviceLogger.addAppender(logs)
        lenient().`when`(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
    }

    @Test
    fun 비어_있거나_64자를_넘는_요청_키는_거절하고_예약을_만들지_않는다() {
        listOf("", "  ", "k".repeat(65)).forEach { key ->
            assertThatThrownBy { service.batchReserveStock(listOf(1L to 1), key) }
                .describedAs("key length=%d", key.length)
                .isInstanceOf(InvalidReservationRequestKeyException::class.java)
        }

        verify(inventoryReservationRepository, never()).findByRequestKey(any())
        verify(inventoryReservationRepository, never()).save(any<InventoryReservation>())
    }

    @Test
    fun 같은_요청_키의_예약과_상품_구성이_다르면_INV009로_거절한다() {
        whenever(inventoryReservationRepository.findByRequestKey(KEY))
            .thenReturn(listOf(InventoryReservation.create(1L, 1, 600L, KEY)))

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 1, 2L to 1), KEY) }
            .isInstanceOf(ReservationRequestKeyConflictException::class.java)
            .extracting { (it as ReservationRequestKeyConflictException).errorCode.code }
            .isEqualTo("INV009")
        verify(inventoryReservationRepository, never()).save(any<InventoryReservation>())
    }

    @Test
    fun 같은_요청_키의_예약이_확정됐으면_키를_풀고_새로_예약한다() {
        val confirmed = InventoryReservation.create(1L, 2, 600L, KEY).apply { status = ReservationStatus.CONFIRMED }
        ReflectionTestUtils.setField(confirmed, "id", 7L)
        val inventory = Inventory.create(productId = 1L, stockQuantity = 10)
        whenever(inventoryReservationRepository.findByRequestKey(KEY)).thenReturn(listOf(confirmed))
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        val result = service.batchReserveStock(listOf(1L to 2), KEY)

        assertThat(result.getValue(1L)).isNotEqualTo(confirmed.reservationId)
        assertThat(inventory.stockQuantity).isEqualTo(8)
        verify(keyedReservationInserter).insert(any())
        verify(inventoryReservationRepository, never()).save(any<InventoryReservation>())
        verify(inventoryReservationRepository).releaseRequestKey(eq(listOf(7L)), eq(KEY), any())
    }

    @Test
    fun 같은_요청_키의_예약이_일부만_확정됐으면_INV009로_거절한다() {
        val confirmed = InventoryReservation.create(1L, 1, 600L, KEY).apply { status = ReservationStatus.CONFIRMED }
        val reserved = InventoryReservation.create(2L, 1, 600L, KEY)
        whenever(inventoryReservationRepository.findByRequestKey(KEY)).thenReturn(listOf(confirmed, reserved))

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 1, 2L to 1), KEY) }
            .isInstanceOf(ReservationRequestKeyConflictException::class.java)
        verify(inventoryReservationRepository, never()).save(any<InventoryReservation>())
    }

    @Test
    fun 유니크_충돌_뒤에도_같은_키의_예약이_보이지_않으면_원래_오류를_다시_던진다() {
        val failure = duplicateKey("uk_reservation_request_product")
        failInsert(failure, afterwards = emptyList())

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 1), KEY) }.isSameAs(failure)
        assertThat(errorMessages()).singleElement().asString().contains("uk_reservation_request_product")
        assertThat(replayCount()).isZero()
    }

    @Test
    fun 유니크_충돌_뒤_같은_키의_예약을_돌려주면_ERROR_없이_재생으로_센다() {
        val winner = InventoryReservation.create(1L, 1, 600L, KEY)
        failInsert(duplicateKey("uk_reservation_request_product"), afterwards = listOf(winner))

        val result = service.batchReserveStock(listOf(1L to 1), KEY)

        assertThat(result).containsExactlyEntriesOf(mapOf(1L to winner.reservationId))
        assertThat(errorMessages()).isEmpty()
        assertThat(replayCount()).isEqualTo(1.0)
    }

    @Test
    fun 유니크_충돌_뒤_재조회가_실패하면_ERROR를_남기고_재조회_오류를_던진다() {
        val lookupFailure = IllegalStateException("lookup failed")
        failInsert(duplicateKey("uk_reservation_request_product"), afterwards = emptyList())
        whenever(inventoryReservationRepository.findByRequestKey(KEY)).thenReturn(emptyList()).thenThrow(lookupFailure)

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 1), KEY) }.isSameAs(lookupFailure)
        assertThat(errorMessages()).hasSize(1)
        assertThat(replayCount()).isZero()
    }

    @Test
    fun 다른_제약_위반은_같은_키의_예약을_돌려줘도_재생_충돌로_세지_않는다() {
        failInsert(duplicateKey("idx_reservation_id"), afterwards = listOf(InventoryReservation.create(1L, 1, 600L, KEY)))

        service.batchReserveStock(listOf(1L to 1), KEY)

        assertThat(replayCount()).isZero()
    }

    private fun failInsert(failure: DataIntegrityViolationException, afterwards: List<InventoryReservation>) {
        whenever(inventoryReservationRepository.findByRequestKey(KEY)).thenReturn(emptyList(), afterwards)
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Inventory.create(productId = 1L, stockQuantity = 10))
        whenever(keyedReservationInserter.insert(any())).thenThrow(failure)
    }

    private fun duplicateKey(constraint: String) =
        DuplicateKeyException("insert", SQLException("Duplicate entry 'x' for key 'inventory_reservations.$constraint'", "23000", 1062))

    private fun errorMessages() = logs.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }

    private fun replayCount() =
        meterRegistry.get("inventory.reserve.replayed").tag("reason", "key_conflict").counter().count()

    companion object {
        private const val KEY = "order-attempt-1"
    }
}
