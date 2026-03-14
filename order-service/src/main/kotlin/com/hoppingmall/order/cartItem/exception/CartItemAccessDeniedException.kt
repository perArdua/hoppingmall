package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode
import com.hoppingmall.order.common.BusinessException

class CartItemAccessDeniedException : BusinessException(CartItemErrorCode.CART_ITEM_ACCESS_DENIED)
