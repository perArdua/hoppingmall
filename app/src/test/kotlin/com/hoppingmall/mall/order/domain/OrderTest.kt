package com.hoppingmall.mall.order.domain

import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderInvalidStatusException
import com.hoppingmall.mall.support.fixture.cancelledFixture
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.paidFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Order")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 주문을_생성한다() {
            val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"))

            assertEquals(1L, order.buyerId)
            assertEquals(BigDecimal("50000"), order.totalAmount)
            assertEquals(OrderStatus.CREATED, order.status)
        }
    }

    @Nested
    @DisplayName("updateStatus")
    inner class UpdateStatus {
        @Test
        fun CREATED에서_PAID로_변경한다() {
            val order = Order.fixture()
            order.updateStatus(OrderStatus.PAID)
            assertEquals(OrderStatus.PAID, order.status)
        }

        @Test
        fun CREATED에서_CANCELLED로_변경한다() {
            val order = Order.fixture()
            order.updateStatus(OrderStatus.CANCELLED)
            assertEquals(OrderStatus.CANCELLED, order.status)
        }

        @Test
        fun PAID에서_SHIPPED로_변경한다() {
            val order = Order.paidFixture()
            order.updateStatus(OrderStatus.SHIPPED)
            assertEquals(OrderStatus.SHIPPED, order.status)
        }

        @Test
        fun PAID에서_CANCELLED로_변경한다() {
            val order = Order.paidFixture()
            order.updateStatus(OrderStatus.CANCELLED)
            assertEquals(OrderStatus.CANCELLED, order.status)
        }

        @Test
        fun SHIPPED에서_DELIVERED로_변경한다() {
            val order = Order.fixture(status = OrderStatus.SHIPPED)
            order.updateStatus(OrderStatus.DELIVERED)
            assertEquals(OrderStatus.DELIVERED, order.status)
        }

        @Test
        fun CREATED에서_SHIPPED로_변경하면_예외가_발생한다() {
            val order = Order.fixture()
            assertThrows(OrderInvalidStatusException::class.java) {
                order.updateStatus(OrderStatus.SHIPPED)
            }
        }

        @Test
        fun DELIVERED에서_다른_상태로_변경하면_예외가_발생한다() {
            val order = Order.fixture(status = OrderStatus.DELIVERED)
            assertThrows(OrderInvalidStatusException::class.java) {
                order.updateStatus(OrderStatus.CANCELLED)
            }
        }

        @Test
        fun CANCELLED에서_다른_상태로_변경하면_예외가_발생한다() {
            val order = Order.cancelledFixture()
            assertThrows(OrderInvalidStatusException::class.java) {
                order.updateStatus(OrderStatus.PAID)
            }
        }
    }

    @Nested
    @DisplayName("isCancellable")
    inner class IsCancellable {
        @Test
        fun CREATED_상태는_취소_가능하다() {
            val order = Order.fixture()
            assertTrue(order.isCancellable())
        }

        @Test
        fun PAID_상태는_취소_가능하다() {
            val order = Order.paidFixture()
            assertTrue(order.isCancellable())
        }

        @Test
        fun SHIPPED_상태는_취소_불가능하다() {
            val order = Order.fixture(status = OrderStatus.SHIPPED)
            assertFalse(order.isCancellable())
        }

        @Test
        fun DELIVERED_상태는_취소_불가능하다() {
            val order = Order.fixture(status = OrderStatus.DELIVERED)
            assertFalse(order.isCancellable())
        }

        @Test
        fun CANCELLED_상태는_취소_불가능하다() {
            val order = Order.cancelledFixture()
            assertFalse(order.isCancellable())
        }
    }

    @Nested
    @DisplayName("isCancelled")
    inner class IsCancelled {
        @Test
        fun CANCELLED_상태이면_true를_반환한다() {
            val order = Order.cancelledFixture()
            assertTrue(order.isCancelled())
        }

        @Test
        fun CANCELLED_상태가_아니면_false를_반환한다() {
            val order = Order.fixture()
            assertFalse(order.isCancelled())
        }
    }
}
