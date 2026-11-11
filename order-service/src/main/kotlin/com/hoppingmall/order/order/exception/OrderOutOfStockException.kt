package com.hoppingmall.order.order.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderOutOfStockException : BusinessException(OrderErrorCode.ORDER_OUT_OF_STOCK)
