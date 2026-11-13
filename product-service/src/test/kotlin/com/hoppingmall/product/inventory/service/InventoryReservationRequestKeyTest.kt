package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InvalidReservationRequestKeyException
import com.hoppingmall.product.inventory.exception.ReservationRequestKeyConflictException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.CacheManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus

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
    private lateinit var transactionManager: PlatformTransactionManager

    @InjectMocks
    private lateinit var service: InventoryCommandServiceImpl

    @BeforeEach
    fun setUp() {
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
    fun 같은_요청_키의_예약이_확정됐으면_그_예약_id를_그대로_돌려준다() {
        val confirmed = InventoryReservation.create(1L, 2, 600L, KEY).apply { status = ReservationStatus.CONFIRMED }
        whenever(inventoryReservationRepository.findByRequestKey(KEY)).thenReturn(listOf(confirmed))

        val result = service.batchReserveStock(listOf(1L to 2), KEY)

        assertThat(result).isEqualTo(mapOf(1L to confirmed.reservationId))
        verify(inventoryReservationRepository, never()).save(any<InventoryReservation>())
    }

    @Test
    fun 유니크_충돌_뒤에도_같은_키의_예약이_보이지_않으면_원래_오류를_다시_던진다() {
        val failure = DataIntegrityViolationException("uk_reservation_request_product")
        whenever(inventoryReservationRepository.findByRequestKey(KEY)).thenReturn(emptyList())
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Inventory.create(productId = 1L, stockQuantity = 10))
        whenever(inventoryReservationRepository.save(any<InventoryReservation>())).thenThrow(failure)

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 1), KEY) }.isSameAs(failure)
    }

    companion object {
        private const val KEY = "order-attempt-1"
    }
}
