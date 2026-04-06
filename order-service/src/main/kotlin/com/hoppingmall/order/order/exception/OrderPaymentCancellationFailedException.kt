package com.hoppingmall.order.order.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class OrderPaymentCancellationFailedException : BusinessException(OrderErrorCode.ORDER_PAYMENT_CANCELLATION_FAILED)
