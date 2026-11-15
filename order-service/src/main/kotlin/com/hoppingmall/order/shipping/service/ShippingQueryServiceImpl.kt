package com.hoppingmall.order.shipping.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.response.ShippingResponse
import com.hoppingmall.order.shipping.exception.ShippingAccessDeniedException
import com.hoppingmall.order.shipping.exception.ShippingNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ShippingQueryServiceImpl(
    private val shippingRepository: ShippingRepository,
    private val orderItemRepository: OrderItemRepository
) : ShippingQueryService {

    override fun getShippingByOrderId(orderId: Long, userId: Long, isAdmin: Boolean): ShippingResponse {
        val shipping = shippingRepository.findByOrderId(orderId)
            ?: throw ShippingNotFoundException()
        checkAccess(shipping, userId, isAdmin)
        return ShippingResponse.from(shipping)
    }

    override fun getShipping(shippingId: Long, userId: Long, isAdmin: Boolean): ShippingResponse {
        val shipping = shippingRepository.findByIdOrNull(shippingId) ?: throw ShippingNotFoundException()
        checkAccess(shipping, userId, isAdmin)
        return ShippingResponse.from(shipping)
    }

    private fun checkAccess(shipping: Shipping, userId: Long, isAdmin: Boolean) {
        if (isAdmin || shipping.buyerId == userId) return
        if (!orderItemRepository.existsByOrderIdAndSellerId(shipping.orderId, userId)) {
            throw ShippingAccessDeniedException()
        }
    }
}
