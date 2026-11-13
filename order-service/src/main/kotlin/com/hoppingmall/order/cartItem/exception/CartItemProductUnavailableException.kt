package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode
import com.hoppingmall.common.BusinessException

class CartItemProductUnavailableException : BusinessException(CartItemErrorCode.CART_ITEM_PRODUCT_UNAVAILABLE)
