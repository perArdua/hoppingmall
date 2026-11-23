package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.order.exception.code.OrderErrorCode

class CartItemSelfPurchaseException : BusinessException(OrderErrorCode.ORDER_SELF_CART_ADD)
