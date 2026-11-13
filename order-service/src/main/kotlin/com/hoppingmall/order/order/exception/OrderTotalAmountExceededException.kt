package com.hoppingmall.order.order.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderTotalAmountExceededException : BusinessException(OrderErrorCode.ORDER_TOTAL_AMOUNT_EXCEEDED)
