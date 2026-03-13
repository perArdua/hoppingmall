package com.hoppingmall.mall.order.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.order.exception.code.OrderErrorCode

class OrderInvalidStatusException : BusinessException(OrderErrorCode.ORDER_INVALID_STATUS)
