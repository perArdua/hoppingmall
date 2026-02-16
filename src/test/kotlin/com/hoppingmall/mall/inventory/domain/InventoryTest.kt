package com.hoppingmall.mall.inventory.domain

import com.hoppingmall.mall.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.mall.support.fixture.emptyStockFixture
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.lowStockFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Inventory")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InventoryTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 재고를_생성한다() {
            // when
            val inventory = Inventory.create(productId = 1L, stockQuantity = 100)

            // then
            assertEquals(1L, inventory.productId)
            assertEquals(100, inventory.stockQuantity)
        }
    }

    @Nested
    @DisplayName("decreaseStock")
    inner class DecreaseStock {
        @Test
        fun 재고를_차감한다() {
            // given
            val inventory = Inventory.fixture(stockQuantity = 100)

            // when
            inventory.decreaseStock(30)

            // then
            assertEquals(70, inventory.stockQuantity)
        }

        @Test
        fun 재고가_부족하면_예외가_발생한다() {
            // given
            val inventory = Inventory.lowStockFixture(stockQuantity = 3)

            // when & then
            assertThrows<InventoryInsufficientStockException> {
                inventory.decreaseStock(5)
            }
        }

        @Test
        fun 재고가_0이면_차감시_예외가_발생한다() {
            // given
            val inventory = Inventory.emptyStockFixture()

            // when & then
            assertThrows<InventoryInsufficientStockException> {
                inventory.decreaseStock(1)
            }
        }
    }

    @Nested
    @DisplayName("increaseStock")
    inner class IncreaseStock {
        @Test
        fun 재고를_증가시킨다() {
            // given
            val inventory = Inventory.fixture(stockQuantity = 50)

            // when
            inventory.increaseStock(30)

            // then
            assertEquals(80, inventory.stockQuantity)
        }
    }

    @Nested
    @DisplayName("hasStock")
    inner class HasStock {
        @Test
        fun 재고가_충분하면_true를_반환한다() {
            // given
            val inventory = Inventory.fixture(stockQuantity = 100)

            // when & then
            assertTrue(inventory.hasStock(50))
        }

        @Test
        fun 재고가_부족하면_false를_반환한다() {
            // given
            val inventory = Inventory.lowStockFixture(stockQuantity = 3)

            // when & then
            assertFalse(inventory.hasStock(5))
        }

        @Test
        fun 재고와_요청_수량이_동일하면_true를_반환한다() {
            // given
            val inventory = Inventory.fixture(stockQuantity = 10)

            // when & then
            assertTrue(inventory.hasStock(10))
        }
    }
}
