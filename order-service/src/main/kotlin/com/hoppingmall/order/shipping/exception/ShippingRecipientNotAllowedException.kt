package com.hoppingmall.order.shipping.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.shipping.exception.code.ShippingErrorCode

class ShippingRecipientNotAllowedException : BusinessException(ShippingErrorCode.SHIPPING_RECIPIENT_NOT_ALLOWED)
