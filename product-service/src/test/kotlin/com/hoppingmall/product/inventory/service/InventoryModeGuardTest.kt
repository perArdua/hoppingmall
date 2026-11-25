package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.InventoryRuntimeMode
import com.hoppingmall.product.inventory.domain.repository.InventoryRuntimeModeStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime

@DisplayName("InventoryModeGuard 단위 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InventoryModeGuardTest {

    @Mock
    private lateinit var inventoryRuntimeModeStore: InventoryRuntimeModeStore

    @Mock
    private lateinit var bucketStockService: BucketStockService

    @InjectMocks
    private lateinit var guard: InventoryModeGuard

    @Test
    fun 기록이_없고_버킷_행도_없으면_single로_기록하고_뜬다() {
        runAsSingle()
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null)
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(false)

        guard.afterSingletonsInstantiated()

        val inserted = insertedMode()
        assertThat(inserted.id).isEqualTo(InventoryRuntimeMode.SINGLETON_ID)
        assertThat(inserted.mode).isEqualTo("single")
        assertThat(inserted.bucketCount).isNull()
    }

    @Test
    fun 기록이_없는_빈_DB에서_bucket으로_뜨면_버킷_수와_함께_기록한다() {
        runAsBucket(8)
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null)
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(false)
        whenever(inventoryRuntimeModeStore.hasInventoryStock()).thenReturn(false)

        guard.afterSingletonsInstantiated()

        val inserted = insertedMode()
        assertThat(inserted.mode).isEqualTo("bucket")
        assertThat(inserted.bucketCount).isEqualTo(8)
    }

    @Test
    fun 기록이_없어도_버킷_행이_있는_DB에서_bucket으로_뜨면_기록한다() {
        runAsBucket(8)
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null)
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(true)

        guard.afterSingletonsInstantiated()

        assertThat(insertedMode().bucketCount).isEqualTo(8)
    }

    @Test
    fun 기록과_같은_모드면_다시_기록하지_않고_뜬다() {
        runAsBucket(8)
        whenever(inventoryRuntimeModeStore.find()).thenReturn(recorded("BUCKET", 8))

        assertThatCode { guard.afterSingletonsInstantiated() }.doesNotThrowAnyException()

        verify(inventoryRuntimeModeStore, never()).insert(any())
    }

    @Test
    fun single_기록의_버킷_수는_비교하지_않는다() {
        runAsSingle()
        whenever(inventoryRuntimeModeStore.find()).thenReturn(recorded("single", 8))

        assertThatCode { guard.afterSingletonsInstantiated() }.doesNotThrowAnyException()
    }

    @Test
    fun DB는_bucket인데_single로_뜨면_두_값과_절차_문서를_담아_기동을_막는다() {
        runAsSingle()
        whenever(inventoryRuntimeModeStore.find()).thenReturn(recorded("bucket", 8))

        assertThatThrownBy { guard.afterSingletonsInstantiated() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DB=bucket(bucket.count=8)")
            .hasMessageContaining("이 인스턴스=single")
            .hasMessageContaining(InventoryModeGuard.PROCEDURE_DOC)
        verify(inventoryRuntimeModeStore, never()).insert(any())
    }

    @Test
    fun 버킷_수가_기록과_다르면_기동을_막는다() {
        runAsBucket(16)
        whenever(inventoryRuntimeModeStore.find()).thenReturn(recorded("bucket", 8))

        assertThatThrownBy { guard.afterSingletonsInstantiated() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DB=bucket(bucket.count=8)")
            .hasMessageContaining("이 인스턴스=bucket(bucket.count=16)")
            .hasMessageContaining(InventoryModeGuard.PROCEDURE_DOC)
    }

    @Test
    fun 첫_기동에_버킷_행이_있는데_single이면_기록하지_않고_기동을_막는다() {
        runAsSingle()
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null)
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(true)

        assertThatThrownBy { guard.afterSingletonsInstantiated() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("inventory_buckets")
            .hasMessageContaining("이 인스턴스=single")
            .hasMessageContaining(InventoryModeGuard.PROCEDURE_DOC)
        verify(inventoryRuntimeModeStore, never()).insert(any())
    }

    @Test
    fun 첫_기동에_버킷_없이_inventories에만_재고가_있는데_bucket이면_기록하지_않고_기동을_막는다() {
        runAsBucket(8)
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null)
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(false)
        whenever(inventoryRuntimeModeStore.hasInventoryStock()).thenReturn(true)

        assertThatThrownBy { guard.afterSingletonsInstantiated() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("inventories")
            .hasMessageContaining("이 인스턴스=bucket(bucket.count=8)")
            .hasMessageContaining(InventoryModeGuard.PROCEDURE_DOC)
        verify(inventoryRuntimeModeStore, never()).insert(any())
    }

    @Test
    fun 동시에_다른_모드의_인스턴스가_먼저_기록했으면_다시_읽은_기록과_비교해_기동을_막는다() {
        runAsSingle()
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null, recorded("bucket", 8))
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(false)
        doThrow(DuplicateKeyException("duplicate id")).whenever(inventoryRuntimeModeStore).insert(any())

        assertThatThrownBy { guard.afterSingletonsInstantiated() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DB=bucket(bucket.count=8)")
            .hasMessageContaining("이 인스턴스=single")
    }

    @Test
    fun 동시에_같은_모드의_인스턴스가_먼저_기록했으면_다시_읽은_기록으로_뜬다() {
        runAsBucket(8)
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null, recorded("bucket", 8))
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(true)
        doThrow(DuplicateKeyException("duplicate id")).whenever(inventoryRuntimeModeStore).insert(any())

        assertThatCode { guard.afterSingletonsInstantiated() }.doesNotThrowAnyException()
    }

    @Test
    fun 중복_키로_실패했는데_다시_읽어도_기록이_없으면_원래_예외를_던진다() {
        runAsSingle()
        whenever(inventoryRuntimeModeStore.find()).thenReturn(null, null)
        whenever(inventoryRuntimeModeStore.hasBuckets()).thenReturn(false)
        doThrow(DuplicateKeyException("duplicate id")).whenever(inventoryRuntimeModeStore).insert(any())

        assertThatThrownBy { guard.afterSingletonsInstantiated() }
            .isInstanceOf(DuplicateKeyException::class.java)
    }

    private fun runAsSingle() {
        whenever(bucketStockService.enabled).thenReturn(false)
    }

    private fun runAsBucket(count: Int) {
        whenever(bucketStockService.enabled).thenReturn(true)
        whenever(bucketStockService.bucketCount).thenReturn(count)
    }

    private fun recorded(mode: String, bucketCount: Int?) =
        InventoryRuntimeMode(InventoryRuntimeMode.SINGLETON_ID, mode, bucketCount, LocalDateTime.now())

    private fun insertedMode(): InventoryRuntimeMode {
        val captor = argumentCaptor<InventoryRuntimeMode>()
        verify(inventoryRuntimeModeStore).insert(captor.capture())
        return captor.firstValue
    }
}
