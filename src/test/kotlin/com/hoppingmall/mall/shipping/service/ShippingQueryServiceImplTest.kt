package com.hoppingmall.mall.shipping.service

import com.hoppingmall.mall.shipping.domain.Shipping
import com.hoppingmall.mall.shipping.domain.repository.ShippingRepository
import com.hoppingmall.mall.shipping.enum.ShippingStatus
import com.hoppingmall.mall.shipping.exception.ShippingNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

@DisplayName("ShippingQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ShippingQueryServiceImplTest {

    private val shippingRepository: ShippingRepository = mock()
    private val shippingQueryService = ShippingQueryServiceImpl(shippingRepository)

    @Nested
    @DisplayName("getShippingByOrderId")
    inner class GetShippingByOrderId {
        @Test
        fun 주문별_배송_정보를_조회한다() {
            // given
            val orderId = 1L
            val shipping = Shipping.fixture(orderId = orderId)

            whenever(shippingRepository.findByOrderId(orderId)).thenReturn(shipping)

            // when
            val response = shippingQueryService.getShippingByOrderId(orderId)

            // then
            assertEquals(orderId, response.orderId)
            assertEquals(ShippingStatus.PREPARING, response.status)
        }

        @Test
        fun 배송_정보가_없으면_예외가_발생한다() {
            // given
            val orderId = 999L

            whenever(shippingRepository.findByOrderId(orderId)).thenReturn(null)

            // when & then
            assertThrows<ShippingNotFoundException> {
                shippingQueryService.getShippingByOrderId(orderId)
            }
        }
    }

    @Nested
    @DisplayName("getShipping")
    inner class GetShipping {
        @Test
        fun 배송_상세_정보를_조회한다() {
            // given
            val shippingId = 1L
            val shipping = Shipping.fixture()

            whenever(shippingRepository.findById(shippingId)).thenReturn(Optional.of(shipping))

            // when
            val response = shippingQueryService.getShipping(shippingId)

            // then
            assertEquals(shippingId, response.id)
            assertEquals("CJ대한통운", response.carrierName)
            assertEquals("1234567890", response.trackingNumber)
        }

        @Test
        fun 배송_정보가_없으면_예외가_발생한다() {
            // given
            val shippingId = 999L

            whenever(shippingRepository.findById(shippingId)).thenReturn(Optional.empty())

            // when & then
            assertThrows<ShippingNotFoundException> {
                shippingQueryService.getShipping(shippingId)
            }
        }
    }
}
