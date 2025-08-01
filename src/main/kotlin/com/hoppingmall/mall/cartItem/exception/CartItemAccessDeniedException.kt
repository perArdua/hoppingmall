package com.hoppingmall.mall.cartItem.exception

import com.hoppingmall.mall.cartItem.exception.code.CartItemErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException

class CartItemAccessDeniedException : BusinessException(CartItemErrorCode.CART_ITEM_ACCESS_DENIED) 