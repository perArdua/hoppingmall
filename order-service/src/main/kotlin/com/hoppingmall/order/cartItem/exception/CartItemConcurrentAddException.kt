package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode
import com.hoppingmall.common.BusinessException

class CartItemConcurrentAddException : BusinessException(CartItemErrorCode.CART_ITEM_CONCURRENT_ADD)
