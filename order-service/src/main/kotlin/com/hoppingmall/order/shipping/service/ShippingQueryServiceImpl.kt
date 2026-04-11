package com.hoppingmall.order.shipping.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.response.ShippingResponse
import com.hoppingmall.order.shipping.exception.ShippingNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ShippingQueryServiceImpl(
    private val shippingRepository: ShippingRepository
) : ShippingQueryService {

    override fun getShippingByOrderId(orderId: Long): ShippingResponse {
        val shipping = shippingRepository.findByOrderId(orderId)
            ?: throw ShippingNotFoundException()
        return ShippingResponse.from(shipping)
    }

    override fun getShipping(shippingId: Long): ShippingResponse {
        val shipping = shippingRepository.findByIdOrNull(shippingId) ?: throw ShippingNotFoundException() 
        return ShippingResponse.from(shipping)
    }
}
