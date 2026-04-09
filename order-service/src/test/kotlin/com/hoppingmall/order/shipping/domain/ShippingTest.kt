package com.hoppingmall.order.shipping.domain

import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.exception.ShippingInvalidStatusException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("Shipping")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ShippingTest {

    private fun createShipping(): Shipping {
        return Shipping.create(
            orderId = 1L,
            buyerId = 10L,
            carrierName = "CJ대한통운",
            trackingNumber = "1234567890",
            recipientName = "홍길동",
            recipientPhone = "010-1234-5678",
            recipientAddress = "서울시 강남구"
        )
    }

    @Test
    fun 배송을_생성한다() {
        val shipping = createShipping()

        assertThat(shipping.orderId).isEqualTo(1L)
        assertThat(shipping.buyerId).isEqualTo(10L)
        assertThat(shipping.status).isEqualTo(ShippingStatus.PREPARING)
        assertThat(shipping.carrierName).isEqualTo("CJ대한통운")
        assertThat(shipping.trackingNumber).isEqualTo("1234567890")
        assertThat(shipping.recipientName).isEqualTo("홍길동")
        assertThat(shipping.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(shipping.recipientAddress).isEqualTo("서울시 강남구")
    }

    @Test
    fun PREPARING에서_IN_TRANSIT으로_상태를_변경한다() {
        val shipping = createShipping()

        shipping.updateStatus(ShippingStatus.IN_TRANSIT)

        assertThat(shipping.status).isEqualTo(ShippingStatus.IN_TRANSIT)
    }

    @Test
    fun PREPARING에서_CANCELLED로_상태를_변경한다() {
        val shipping = createShipping()

        shipping.updateStatus(ShippingStatus.CANCELLED)

        assertThat(shipping.status).isEqualTo(ShippingStatus.CANCELLED)
    }

    @Test
    fun IN_TRANSIT에서_DELIVERED로_상태를_변경한다() {
        val shipping = createShipping()
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)

        shipping.updateStatus(ShippingStatus.DELIVERED)

        assertThat(shipping.status).isEqualTo(ShippingStatus.DELIVERED)
    }

    @Test
    fun IN_TRANSIT에서_FAILED로_상태를_변경한다() {
        val shipping = createShipping()
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)

        shipping.updateStatus(ShippingStatus.FAILED)

        assertThat(shipping.status).isEqualTo(ShippingStatus.FAILED)
    }

    @Test
    fun PREPARING에서_DELIVERED로_직접_변경하면_예외가_발생한다() {
        val shipping = createShipping()

        assertThatThrownBy { shipping.updateStatus(ShippingStatus.DELIVERED) }
            .isInstanceOf(ShippingInvalidStatusException::class.java)
    }

    @Test
    fun DELIVERED에서_상태변경하면_예외가_발생한다() {
        val shipping = createShipping()
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(ShippingStatus.DELIVERED)

        assertThatThrownBy { shipping.updateStatus(ShippingStatus.IN_TRANSIT) }
            .isInstanceOf(ShippingInvalidStatusException::class.java)
    }

    @Test
    fun FAILED에서_상태변경하면_예외가_발생한다() {
        val shipping = createShipping()
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(ShippingStatus.FAILED)

        assertThatThrownBy { shipping.updateStatus(ShippingStatus.PREPARING) }
            .isInstanceOf(ShippingInvalidStatusException::class.java)
    }

    @Test
    fun CANCELLED에서_상태변경하면_예외가_발생한다() {
        val shipping = createShipping()
        shipping.updateStatus(ShippingStatus.CANCELLED)

        assertThatThrownBy { shipping.updateStatus(ShippingStatus.PREPARING) }
            .isInstanceOf(ShippingInvalidStatusException::class.java)
    }
}
