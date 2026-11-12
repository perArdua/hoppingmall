package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

@DisplayName("BucketReservationExpiryJob 단위 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class BucketReservationExpiryJobTest {

    @Mock
    private lateinit var reservationRepository: InventoryReservationRepository

    @Mock
    private lateinit var bucketStockService: BucketStockService

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private val meterRegistry = SimpleMeterRegistry()
    private val statuses = mutableListOf<SimpleTransactionStatus>()

    @BeforeEach
    fun setUp() {
        lenient().`when`(transactionManager.getTransaction(any())).thenAnswer {
            SimpleTransactionStatus().also { statuses.add(it) }
        }
    }

    private fun job(enabled: Boolean = true, chunkSize: Int = 2) =
        BucketReservationExpiryJob(reservationRepository, bucketStockService, transactionManager, meterRegistry, enabled, chunkSize)

    private fun rows(vararg idAndQuantity: Pair<Long, Int>): List<Array<Any>> =
        idAndQuantity.map { arrayOf<Any>(it.first, it.second) }

    @Test
    fun 꺼져_있으면_조회하지_않는다() {
        job(enabled = false).expireReservations()

        verify(reservationRepository, never()).countExpiredGroupByProduct(any())
    }

    @Test
    fun 만료_적체가_없으면_아무것도_하지_않는다() {
        whenever(reservationRepository.countExpiredGroupByProduct(any())).thenReturn(emptyList())

        job().expireReservations()

        verify(reservationRepository, never()).findExpiredChunk(any(), any(), any())
    }

    @Test
    fun 청크가_가득_차면_다음_청크를_이어서_처리한다() {
        whenever(reservationRepository.countExpiredGroupByProduct(any())).thenReturn(listOf(arrayOf<Any>(7L, 3L)))
        whenever(reservationRepository.findExpiredChunk(eq(7L), any(), eq(2)))
            .thenReturn(rows(1L to 1, 2L to 2))
            .thenReturn(rows(3L to 4))
        whenever(reservationRepository.expireByIds(eq(listOf(1L, 2L)), any())).thenReturn(2)
        whenever(reservationRepository.expireByIds(eq(listOf(3L)), any())).thenReturn(1)

        job().expireReservations()

        verify(bucketStockService).restore(7L, 3)
        verify(bucketStockService).restore(7L, 4)
        assertThat(meterRegistry.summary("inventory.expiry.batch.size").count()).isEqualTo(2)
    }

    @Test
    fun CAS_갱신_수가_다르면_그_청크를_롤백하고_다시_읽는다() {
        whenever(reservationRepository.findExpiredChunk(eq(7L), any(), eq(2)))
            .thenReturn(rows(1L to 1, 2L to 2))
            .thenReturn(rows(2L to 2))
        whenever(reservationRepository.expireByIds(eq(listOf(1L, 2L)), any())).thenReturn(1)
        whenever(reservationRepository.expireByIds(eq(listOf(2L)), any())).thenReturn(1)

        job().drainProduct(7L, java.time.LocalDateTime.now())

        verify(bucketStockService, never()).restore(7L, 3)
        verify(bucketStockService).restore(7L, 2)
        assertThat(statuses[0].isRollbackOnly).isTrue()
        assertThat(statuses[1].isRollbackOnly).isFalse()
        assertThat(meterRegistry.counter("inventory.expiry.cas_mismatch").count()).isEqualTo(1.0)
        verify(transactionManager).commit(statuses[0] as TransactionStatus)
    }

    @Test
    fun CAS_불일치가_연속_3회면_이번_주기를_멈춘다() {
        whenever(reservationRepository.findExpiredChunk(eq(7L), any(), eq(2))).thenReturn(rows(1L to 1))
        whenever(reservationRepository.expireByIds(eq(listOf(1L)), any())).thenReturn(0)

        job().drainProduct(7L, java.time.LocalDateTime.now())

        verify(reservationRepository, times(3)).findExpiredChunk(eq(7L), any(), eq(2))
        verify(bucketStockService, never()).restore(any(), any())
        assertThat(meterRegistry.counter("inventory.expiry.cas_mismatch").count()).isEqualTo(3.0)
    }

    @Test
    fun 한_상품이_실패해도_다음_상품을_처리한다() {
        whenever(reservationRepository.countExpiredGroupByProduct(any()))
            .thenReturn(listOf(arrayOf<Any>(9L, 1L), arrayOf<Any>(8L, 1L)))
        whenever(reservationRepository.findExpiredChunk(eq(8L), any(), eq(2))).thenThrow(IllegalStateException("db down"))
        whenever(reservationRepository.findExpiredChunk(eq(9L), any(), eq(2))).thenReturn(rows(5L to 1))
        whenever(reservationRepository.expireByIds(eq(listOf(5L)), any())).thenReturn(1)

        job().expireReservations()

        verify(bucketStockService).restore(9L, 1)
    }
}
