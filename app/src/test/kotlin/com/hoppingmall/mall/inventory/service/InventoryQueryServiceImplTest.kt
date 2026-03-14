package com.hoppingmall.mall.inventory.service

import com.hoppingmall.mall.inventory.domain.Inventory
import com.hoppingmall.mall.inventory.domain.repository.InventoryRepository
import com.hoppingmall.mall.inventory.exception.InventoryNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("InventoryQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InventoryQueryServiceImplTest {

    private val inventoryRepository: InventoryRepository = mock()
    private val inventoryQueryService = InventoryQueryServiceImpl(inventoryRepository)

    @Nested
    @DisplayName("getStock")
    inner class GetStock {
        @Test
        fun 재고를_조회한다() {
            // given
            val productId = 1L
            val inventory = Inventory.fixture(productId = productId, stockQuantity = 100).withId(1L)

            whenever(inventoryRepository.findByProductId(productId)).thenReturn(inventory)

            // when
            val response = inventoryQueryService.getStock(productId)

            // then
            assertEquals(1L, response.id)
            assertEquals(productId, response.productId)
            assertEquals(100, response.stockQuantity)
        }

        @Test
        fun 재고가_존재하지_않으면_예외가_발생한다() {
            // given
            val productId = 999L

            whenever(inventoryRepository.findByProductId(productId)).thenReturn(null)

            // when & then
            assertThrows<InventoryNotFoundException> {
                inventoryQueryService.getStock(productId)
            }
        }
    }
}
