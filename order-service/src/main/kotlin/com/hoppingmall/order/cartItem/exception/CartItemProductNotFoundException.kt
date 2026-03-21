package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode
import com.hoppingmall.common.BusinessException

class CartItemProductNotFoundException : BusinessException(CartItemErrorCode.CART_ITEM_PRODUCT_NOT_FOUND)
