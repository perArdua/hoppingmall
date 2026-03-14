package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode
import com.hoppingmall.order.common.BusinessException

class CartItemNotFoundException : BusinessException(CartItemErrorCode.CART_ITEM_NOT_FOUND)
