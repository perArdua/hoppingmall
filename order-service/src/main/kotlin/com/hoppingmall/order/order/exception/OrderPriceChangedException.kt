package com.hoppingmall.order.order.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.dto.response.OrderPriceChange
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderPriceChangedException(
    val changes: List<OrderPriceChange>
) : BusinessException(OrderErrorCode.ORDER_PRICE_CHANGED)
