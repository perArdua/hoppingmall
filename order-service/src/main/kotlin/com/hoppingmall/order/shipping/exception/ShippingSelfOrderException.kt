package com.hoppingmall.order.shipping.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.shipping.exception.code.ShippingErrorCode

class ShippingSelfOrderException : BusinessException(ShippingErrorCode.SHIPPING_SELF_ORDER)
