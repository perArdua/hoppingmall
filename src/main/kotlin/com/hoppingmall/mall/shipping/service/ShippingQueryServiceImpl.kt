package com.hoppingmall.mall.shipping.service

import com.hoppingmall.mall.shipping.domain.repository.ShippingRepository
import com.hoppingmall.mall.shipping.dto.response.ShippingResponse
import com.hoppingmall.mall.shipping.exception.ShippingNotFoundException
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
        val shipping = shippingRepository.findById(shippingId)
            .orElseThrow { ShippingNotFoundException() }
        return ShippingResponse.from(shipping)
    }
}
