package com.hoppingmall.order.order.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderShippingAddressRequiredException : BusinessException(OrderErrorCode.ORDER_SHIPPING_ADDRESS_REQUIRED)
