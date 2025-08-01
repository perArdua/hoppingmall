package com.hoppingmall.mall.cartItem.exception

import com.hoppingmall.mall.cartItem.exception.code.CartItemErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException

class CartItemNotFoundException : BusinessException(CartItemErrorCode.CART_ITEM_NOT_FOUND) 