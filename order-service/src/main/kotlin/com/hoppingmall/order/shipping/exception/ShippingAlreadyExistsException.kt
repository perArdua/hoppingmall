package com.hoppingmall.order.shipping.exception

import com.hoppingmall.order.common.BusinessException
import com.hoppingmall.order.shipping.exception.code.ShippingErrorCode

class ShippingAlreadyExistsException : BusinessException(ShippingErrorCode.SHIPPING_ALREADY_EXISTS)
