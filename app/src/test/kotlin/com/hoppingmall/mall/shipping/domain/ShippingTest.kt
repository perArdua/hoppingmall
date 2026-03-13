package com.hoppingmall.mall.shipping.domain

import com.hoppingmall.mall.shipping.enum.ShippingStatus
import com.hoppingmall.mall.shipping.exception.ShippingInvalidStatusException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.inTransitFixture
import com.hoppingmall.mall.support.fixture.deliveredFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Shipping")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ShippingTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 배송_정보를_생성한다() {
            // when
            val shipping = Shipping.create(
                orderId = 1L,
                buyerId = 1L,
                carrierName = "CJ대한통운",
                trackingNumber = "1234567890",
                recipientName = "홍길동",
                recipientPhone = "010-1234-5678",
                recipientAddress = "서울시 강남구 테헤란로 123"
            )

            // then
            assertEquals(1L, shipping.orderId)
            assertEquals(1L, shipping.buyerId)
            assertEquals(ShippingStatus.PREPARING, shipping.status)
            assertEquals("CJ대한통운", shipping.carrierName)
            assertEquals("1234567890", shipping.trackingNumber)
            assertEquals("홍길동", shipping.recipientName)
            assertEquals("010-1234-5678", shipping.recipientPhone)
            assertEquals("서울시 강남구 테헤란로 123", shipping.recipientAddress)
        }
    }

    @Nested
    @DisplayName("updateStatus")
    inner class UpdateStatus {
        @Test
        fun PREPARING에서_IN_TRANSIT로_변경한다() {
            // given
            val shipping = Shipping.fixture()

            // when
            shipping.updateStatus(ShippingStatus.IN_TRANSIT)

            // then
            assertEquals(ShippingStatus.IN_TRANSIT, shipping.status)
        }

        @Test
        fun IN_TRANSIT에서_DELIVERED로_변경한다() {
            // given
            val shipping = Shipping.inTransitFixture()

            // when
            shipping.updateStatus(ShippingStatus.DELIVERED)

            // then
            assertEquals(ShippingStatus.DELIVERED, shipping.status)
        }

        @Test
        fun PREPARING에서_DELIVERED로_직접_변경하면_예외가_발생한다() {
            // given
            val shipping = Shipping.fixture()

            // when & then
            assertThrows<ShippingInvalidStatusException> {
                shipping.updateStatus(ShippingStatus.DELIVERED)
            }
        }

        @Test
        fun DELIVERED에서_상태를_변경하면_예외가_발생한다() {
            // given
            val shipping = Shipping.deliveredFixture()

            // when & then
            assertThrows<ShippingInvalidStatusException> {
                shipping.updateStatus(ShippingStatus.IN_TRANSIT)
            }
        }
    }
}
