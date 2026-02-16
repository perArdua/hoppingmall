package com.hoppingmall.mall.order.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.order.exception.code.OrderErrorCode

class OrderEmptyItemsException : BusinessException(OrderErrorCode.ORDER_EMPTY_ITEMS)
