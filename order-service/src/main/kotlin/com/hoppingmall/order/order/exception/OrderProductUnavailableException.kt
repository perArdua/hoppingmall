package com.hoppingmall.order.order.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderProductUnavailableException : BusinessException(OrderErrorCode.ORDER_PRODUCT_UNAVAILABLE)
