package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.order.api.OrderItemInfo
import com.hoppingmall.mall.order.api.OrderItemQueryPort
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderItemQueryPortAdapter(
    private val orderItemRepository: OrderItemRepository
) : OrderItemQueryPort {

    override fun findDeliveredItemsBySellerAndPeriod(
        sellerId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<OrderItemInfo> {
        return orderItemRepository.findDeliveredItemsBySellerAndPeriod(sellerId, startDate, endDate)
            .map { item ->
                OrderItemInfo(
                    id = item.id!!,
                    orderId = item.orderId,
                    sellerId = item.sellerId,
                    productId = item.productId,
                    productName = item.productName,
                    productPrice = item.productPrice,
                    quantity = item.quantity,
                    totalPrice = item.totalPrice
                )
            }
    }
}
