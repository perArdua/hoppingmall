package com.hoppingmall.order.order.exception

import com.hoppingmall.order.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderAccessDeniedException : BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED)
