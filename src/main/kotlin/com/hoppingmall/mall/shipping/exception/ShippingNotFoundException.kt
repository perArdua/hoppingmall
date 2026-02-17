package com.hoppingmall.mall.shipping.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.shipping.exception.code.ShippingErrorCode

class ShippingNotFoundException : BusinessException(ShippingErrorCode.SHIPPING_NOT_FOUND)
