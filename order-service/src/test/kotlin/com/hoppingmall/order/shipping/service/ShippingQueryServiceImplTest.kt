package com.hoppingmall.order.shipping.service

import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.exception.ShippingNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@DisplayName("ShippingQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ShippingQueryServiceImplTest {

    @Mock
    private lateinit var shippingRepository: ShippingRepository

    @InjectMocks
    private lateinit var service: ShippingQueryServiceImpl

    private fun createShipping(id: Long = 1L): Shipping {
        val shipping = Shipping.create(
            orderId = 10L, buyerId = 1L, carrierName = "CJ대한통운",
            trackingNumber = "1234567890", recipientName = "홍길동",
            recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
        )
        ReflectionTestUtils.setField(shipping, "id", id)
        return shipping
    }

    @Test
    fun 주문ID로_배송을_조회한다() {
        val shipping = createShipping()

        whenever(shippingRepository.findByOrderId(10L)).thenReturn(shipping)

        val result = service.getShippingByOrderId(10L)

        assertThat(result.orderId).isEqualTo(10L)
        assertThat(result.carrierName).isEqualTo("CJ대한통운")
    }

    @Test
    fun 주문ID로_배송이_없으면_예외가_발생한다() {
        whenever(shippingRepository.findByOrderId(999L)).thenReturn(null)

        assertThatThrownBy { service.getShippingByOrderId(999L) }
            .isInstanceOf(ShippingNotFoundException::class.java)
    }

    @Test
    fun 배송ID로_배송을_조회한다() {
        val shipping = createShipping()

        whenever(shippingRepository.findById(1L)).thenReturn(Optional.of(shipping))

        val result = service.getShipping(1L)

        assertThat(result.id).isEqualTo(1L)
    }

    @Test
    fun 배송ID로_배송이_없으면_예외가_발생한다() {
        whenever(shippingRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.getShipping(999L) }
            .isInstanceOf(ShippingNotFoundException::class.java)
    }
}
