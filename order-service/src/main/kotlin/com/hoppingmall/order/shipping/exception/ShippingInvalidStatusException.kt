package com.hoppingmall.order.shipping.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.shipping.exception.code.ShippingErrorCode

class ShippingInvalidStatusException : BusinessException(ShippingErrorCode.SHIPPING_INVALID_STATUS)
