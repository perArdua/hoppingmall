package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode

class CartItemOutOfStockException : BusinessException(CartItemErrorCode.CART_ITEM_OUT_OF_STOCK)
