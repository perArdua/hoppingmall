package com.hoppingmall.order.order.exception

import com.hoppingmall.order.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderEmptyItemsException : BusinessException(OrderErrorCode.ORDER_EMPTY_ITEMS)
