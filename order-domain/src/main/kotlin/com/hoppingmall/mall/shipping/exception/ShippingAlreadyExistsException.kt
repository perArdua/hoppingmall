package com.hoppingmall.mall.shipping.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.shipping.exception.code.ShippingErrorCode

class ShippingAlreadyExistsException : BusinessException(ShippingErrorCode.SHIPPING_ALREADY_EXISTS)
