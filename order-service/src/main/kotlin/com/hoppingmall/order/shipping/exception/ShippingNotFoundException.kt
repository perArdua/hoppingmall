package com.hoppingmall.order.shipping.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.shipping.exception.code.ShippingErrorCode

class ShippingNotFoundException : BusinessException(ShippingErrorCode.SHIPPING_NOT_FOUND)
