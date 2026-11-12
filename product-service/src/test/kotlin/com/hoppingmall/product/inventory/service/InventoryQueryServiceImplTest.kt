package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("InventoryQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InventoryQueryServiceImplTest {

    @Mock
    private lateinit var inventoryRepository: InventoryRepository

    @Mock
    private lateinit var bucketStockService: BucketStockService

    @InjectMocks
    private lateinit var service: InventoryQueryServiceImpl

    @Test
    fun 재고를_조회한다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)

        whenever(inventoryRepository.findByProductId(1L)).thenReturn(inventory)

        val result = service.getStock(1L)

        assertThat(result.stockQuantity).isEqualTo(100)
    }

    @Test
    fun bucket_모드에서는_버킷_합을_재고_수량으로_돌려준다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)

        whenever(inventoryRepository.findByProductId(1L)).thenReturn(inventory)
        whenever(bucketStockService.enabled).thenReturn(true)
        whenever(bucketStockService.totalStock(1L)).thenReturn(93)

        val result = service.getStock(1L)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.productId).isEqualTo(1L)
        assertThat(result.stockQuantity).isEqualTo(93)
    }

    @Test
    fun single_모드에서는_버킷을_읽지_않는다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)

        whenever(inventoryRepository.findByProductId(1L)).thenReturn(inventory)

        service.getStock(1L)

        verify(bucketStockService, never()).totalStock(any())
    }

    @Test
    fun 존재하지_않는_재고_조회_시_예외를_발생시킨다() {
        whenever(inventoryRepository.findByProductId(999L)).thenReturn(null)

        assertThatThrownBy { service.getStock(999L) }
            .isInstanceOf(InventoryNotFoundException::class.java)
    }
}
