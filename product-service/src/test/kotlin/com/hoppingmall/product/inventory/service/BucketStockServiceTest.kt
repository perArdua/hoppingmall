package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.InventoryBucket
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker.BucketLock
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker.LockedBucket
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("BucketStockService 단위 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class BucketStockServiceTest {

    @Mock
    private lateinit var bucketRepository: InventoryBucketRepository

    @Mock
    private lateinit var locker: InventoryBucketLocker

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var service: BucketStockService

    private val productId = 1L
    private val bucketCount = 4

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        service = BucketStockService(bucketRepository, locker, meterRegistry, "bucket", bucketCount)
    }

    private fun startOf(key: String) = Math.floorMod(key.hashCode(), bucketCount)

    private fun counter(name: String) = meterRegistry.counter(name).count()

    @Nested
    @DisplayName("모드와 설정")
    inner class Mode {

        @Test
        fun bucket_모드일_때만_활성화된다() {
            assertThat(service.enabled).isTrue()
            assertThat(BucketStockService(bucketRepository, locker, meterRegistry, "single", 8).enabled).isFalse()
            assertThat(BucketStockService(bucketRepository, locker, meterRegistry, "BUCKET", 8).enabled).isTrue()
        }

        @Test
        fun 버킷_수가_0이하면_생성할_수_없다() {
            assertThatThrownBy { BucketStockService(bucketRepository, locker, meterRegistry, "bucket", 0) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun 재고를_K개_버킷으로_나눠_저장한다() {
            service.initBuckets(productId, 10)

            verify(bucketRepository).saveAll(org.mockito.kotlin.argThat<List<InventoryBucket>> {
                map { it.stockQuantity } == listOf(4, 2, 2, 2)
            })
        }
    }

    @Nested
    @DisplayName("예약 탐색")
    inner class Reserve {

        @Test
        fun 첫_버킷이_충분하면_바로_차감한다() {
            val key = "rsv-a"
            val start = startOf(key)
            whenever(locker.lockNowait(productId, start)).thenReturn(BucketLock.Acquired(5))
            whenever(locker.decrease(productId, start, 2)).thenReturn(1)

            service.reserve(productId, 2, key)

            verify(locker).decrease(productId, start, 2)
            assertThat(meterRegistry.summary("inventory.reserve.bucket_attempts").totalAmount()).isEqualTo(1.0)
        }

        @Test
        fun 바쁜_버킷은_건너뛰고_다음_버킷에서_차감한다() {
            val key = "rsv-b"
            val start = startOf(key)
            val next = (start + 1) % bucketCount
            whenever(locker.lockNowait(productId, start)).thenReturn(BucketLock.Busy)
            whenever(locker.lockNowait(productId, next)).thenReturn(BucketLock.Acquired(3))
            whenever(locker.decrease(productId, next, 1)).thenReturn(1)

            service.reserve(productId, 1, key)

            assertThat(counter("inventory.reserve.bucket_busy")).isEqualTo(1.0)
            verify(locker, never()).lockWaiting(any(), any())
        }

        @Test
        fun 모든_버킷이_바쁘고_쥔_행이_없으면_시작_버킷을_기다려_차감한다() {
            val key = "rsv-c"
            val start = startOf(key)
            whenever(locker.lockNowait(eq(productId), any())).thenReturn(BucketLock.Busy)
            whenever(locker.lockWaiting(productId, start)).thenReturn(BucketLock.Acquired(10))
            whenever(locker.decrease(productId, start, 1)).thenReturn(1)

            service.reserve(productId, 1, key)

            assertThat(counter("inventory.reserve.wait")).isEqualTo(1.0)
            assertThat(counter("inventory.reserve.bucket_busy")).isEqualTo(bucketCount.toDouble())
        }

        @Test
        fun 기다린_시작_버킷이_부족하면_재시작을_요청한다() {
            val key = "rsv-d"
            val start = startOf(key)
            whenever(locker.lockNowait(eq(productId), any())).thenReturn(BucketLock.Busy)
            whenever(locker.lockWaiting(productId, start)).thenReturn(BucketLock.Acquired(0))

            assertThatThrownBy { service.reserve(productId, 1, key) }
                .isInstanceOf(BucketWalkExhaustedException::class.java)
            verify(locker, never()).decrease(any(), any(), any())
        }

        @Test
        fun 부족한_버킷을_하나라도_잠갔으면_기다리지_않고_재시작을_요청한다() {
            val key = "rsv-e"
            val start = startOf(key)
            whenever(locker.lockNowait(eq(productId), any())).thenReturn(BucketLock.Busy)
            whenever(locker.lockNowait(productId, start)).thenReturn(BucketLock.Acquired(0))

            assertThatThrownBy { service.reserve(productId, 1, key) }
                .isInstanceOf(BucketWalkExhaustedException::class.java)
            verify(locker, never()).lockWaiting(any(), any())
            assertThat(counter("inventory.reserve.wait")).isZero()
        }

        @Test
        fun 버킷_행이_없으면_부족과_같이_재시작을_요청한다() {
            whenever(locker.lockNowait(eq(productId), any())).thenReturn(BucketLock.Missing)

            assertThatThrownBy { service.reserve(productId, 1, "rsv-f") }
                .isInstanceOf(BucketWalkExhaustedException::class.java)
            verify(locker, never()).lockWaiting(any(), any())
        }

        @Test
        fun 차감_UPDATE가_0행이면_부족으로_취급한다() {
            whenever(locker.lockNowait(eq(productId), any())).thenReturn(BucketLock.Acquired(5))
            whenever(locker.decrease(eq(productId), any(), eq(1))).thenReturn(0)

            assertThatThrownBy { service.reserve(productId, 1, "rsv-g") }
                .isInstanceOf(BucketWalkExhaustedException::class.java)
        }
    }

    @Nested
    @DisplayName("전체 잠금 폴백")
    inner class LockAll {

        private fun locked(vararg stocks: Int) = stocks.mapIndexed { bucketNo, stock -> LockedBucket(bucketNo, stock) }

        @Test
        fun bucket_no_순서로_여러_버킷에서_나눠_차감한다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(locked(1, 0, 3))

            service.reserveLockingAll(productId, 3)

            verify(locker).decrease(productId, 0, 1)
            verify(locker, never()).decrease(productId, 1, 0)
            verify(locker).decrease(productId, 2, 2)
            assertThat(counter("inventory.reserve.fallback")).isEqualTo(1.0)
        }

        @Test
        fun 합계가_부족하면_재고_부족_예외를_던진다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(locked(1))

            assertThatThrownBy { service.reserveLockingAll(productId, 2) }
                .isInstanceOf(InventoryInsufficientStockException::class.java)
            assertThat(meterRegistry.counter("inventory.reserve.rejected", "reason", "insufficient").count()).isEqualTo(1.0)
            verify(locker, never()).decrease(any(), any(), any())
        }

        @Test
        fun 버킷이_없으면_재고_없음_예외를_던진다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(emptyList())

            assertThatThrownBy { service.decrease(productId, 1) }
                .isInstanceOf(InventoryNotFoundException::class.java)
        }

        @Test
        fun 직접_차감은_탐색_없이_전체_잠금으로_한다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(locked(2))

            service.decrease(productId, 2)

            verify(locker).decrease(productId, 0, 2)
            verify(locker, never()).lockNowait(any(), any())
        }
    }

    @Nested
    @DisplayName("복구와 조회")
    inner class RestoreAndQuery {

        private fun locked(vararg stocks: Int) = stocks.mapIndexed { bucketNo, stock -> LockedBucket(bucketNo, stock) }

        @Test
        fun 전체를_잠그고_가장_적은_버킷에_복구한다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(locked(5, 1, 1, 7))

            service.restore(productId, 3)

            verify(locker).increase(productId, 1, 3)
        }

        @Test
        fun 복구_수량이_0이하면_아무것도_하지_않는다() {
            service.restore(productId, 0)

            verify(locker, never()).lockAll(any(), any())
        }

        @Test
        fun 복구할_버킷이_없으면_예외를_던진다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(emptyList())

            assertThatThrownBy { service.restore(productId, 1) }
                .isInstanceOf(InventoryNotFoundException::class.java)
        }

        @Test
        fun 재고를_버킷에_다시_나눈다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(locked(0, 0, 0, 0))

            service.redistribute(productId, 9)

            verify(locker).setStock(productId, 0, 3)
            verify(locker).setStock(productId, 1, 2)
            verify(locker).setStock(productId, 2, 2)
            verify(locker).setStock(productId, 3, 2)
        }

        @Test
        fun 재분배할_버킷이_없으면_예외를_던진다() {
            whenever(locker.lockAll(productId, bucketCount)).thenReturn(emptyList())

            assertThatThrownBy { service.redistribute(productId, 1) }
                .isInstanceOf(InventoryNotFoundException::class.java)
        }

        @Test
        fun 버킷_합계와_존재_여부를_조회한다() {
            whenever(bucketRepository.sumStock(productId)).thenReturn(12L)
            whenever(bucketRepository.countByProductId(productId)).thenReturn(4L)

            assertThat(service.totalStock(productId)).isEqualTo(12)
            assertThat(service.hasBuckets(productId)).isTrue()
        }

        @Test
        fun 재시작_수를_기록한다() {
            service.recordRestart()

            assertThat(counter("inventory.reserve.restart")).isEqualTo(1.0)
            verify(bucketRepository, never()).sumStock(anyOrNull())
        }
    }
}
