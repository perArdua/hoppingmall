package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode

class CartItemProductNotOnSaleException : BusinessException(CartItemErrorCode.CART_ITEM_PRODUCT_NOT_ON_SALE)
