package com.hoppingmall.mall.inventory.service

import com.hoppingmall.mall.inventory.domain.Inventory
import com.hoppingmall.mall.inventory.domain.repository.InventoryRepository
import com.hoppingmall.mall.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.mall.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.mall.inventory.exception.InventoryAlreadyExistsException
import com.hoppingmall.mall.inventory.exception.InventoryNotFoundException
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

@DisplayName("InventoryCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InventoryCommandServiceImplTest {

    private val inventoryRepository: InventoryRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val inventoryCommandService = InventoryCommandServiceImpl(inventoryRepository, productRepository)

    @Nested
    @DisplayName("initStock")
    inner class InitStock {
        @Test
        fun 재고를_초기화한다() {
            // given
            val request = InventoryInitRequest(productId = 1L, stockQuantity = 100)

            whenever(productRepository.existsById(1L)).thenReturn(true)
            whenever(inventoryRepository.existsByProductId(1L)).thenReturn(false)
            whenever(inventoryRepository.save(any<Inventory>())).thenAnswer { invocation ->
                invocation.getArgument<Inventory>(0).withId(1L)
            }

            // when
            val response = inventoryCommandService.initStock(request)

            // then
            assertEquals(1L, response.id)
            assertEquals(1L, response.productId)
            assertEquals(100, response.stockQuantity)
        }

        @Test
        fun 상품이_존재하지_않으면_예외가_발생한다() {
            // given
            val request = InventoryInitRequest(productId = 999L, stockQuantity = 100)

            whenever(productRepository.existsById(999L)).thenReturn(false)

            // when & then
            assertThrows<ProductNotFoundException> {
                inventoryCommandService.initStock(request)
            }
        }

        @Test
        fun 이미_재고가_존재하면_예외가_발생한다() {
            // given
            val request = InventoryInitRequest(productId = 1L, stockQuantity = 100)

            whenever(productRepository.existsById(1L)).thenReturn(true)
            whenever(inventoryRepository.existsByProductId(1L)).thenReturn(true)

            // when & then
            assertThrows<InventoryAlreadyExistsException> {
                inventoryCommandService.initStock(request)
            }
        }
    }

    @Nested
    @DisplayName("updateStock")
    inner class UpdateStock {
        @Test
        fun 재고_수량을_변경한다() {
            // given
            val productId = 1L
            val request = InventoryUpdateRequest(stockQuantity = 200)
            val inventory = Inventory.fixture(productId = productId, stockQuantity = 100).withId(1L)

            whenever(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(inventory)

            // when
            val response = inventoryCommandService.updateStock(productId, request)

            // then
            assertEquals(200, response.stockQuantity)
        }

        @Test
        fun 재고가_존재하지_않으면_예외가_발생한다() {
            // given
            val productId = 999L
            val request = InventoryUpdateRequest(stockQuantity = 200)

            whenever(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(null)

            // when & then
            assertThrows<InventoryNotFoundException> {
                inventoryCommandService.updateStock(productId, request)
            }
        }
    }

    @Nested
    @DisplayName("decreaseStock")
    inner class DecreaseStock {
        @Test
        fun 재고를_차감한다() {
            // given
            val productId = 1L
            val inventory = Inventory.fixture(productId = productId, stockQuantity = 100).withId(1L)

            whenever(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(inventory)

            // when
            inventoryCommandService.decreaseStock(productId, 30)

            // then
            assertEquals(70, inventory.stockQuantity)
        }

        @Test
        fun 재고가_존재하지_않으면_예외가_발생한다() {
            // given
            val productId = 999L

            whenever(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(null)

            // when & then
            assertThrows<InventoryNotFoundException> {
                inventoryCommandService.decreaseStock(productId, 10)
            }
        }
    }

    @Nested
    @DisplayName("increaseStock")
    inner class IncreaseStock {
        @Test
        fun 재고를_증가시킨다() {
            // given
            val productId = 1L
            val inventory = Inventory.fixture(productId = productId, stockQuantity = 50).withId(1L)

            whenever(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(inventory)

            // when
            inventoryCommandService.increaseStock(productId, 30)

            // then
            assertEquals(80, inventory.stockQuantity)
        }

        @Test
        fun 재고가_존재하지_않으면_예외가_발생한다() {
            // given
            val productId = 999L

            whenever(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(null)

            // when & then
            assertThrows<InventoryNotFoundException> {
                inventoryCommandService.increaseStock(productId, 10)
            }
        }
    }
}
