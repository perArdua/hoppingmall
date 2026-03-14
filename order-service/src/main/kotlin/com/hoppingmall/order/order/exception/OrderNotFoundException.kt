package com.hoppingmall.order.order.exception

import com.hoppingmall.order.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderNotFoundException : BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
