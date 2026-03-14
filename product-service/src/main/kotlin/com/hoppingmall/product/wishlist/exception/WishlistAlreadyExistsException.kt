package com.hoppingmall.product.wishlist.exception

import com.hoppingmall.product.common.BusinessException
import com.hoppingmall.product.wishlist.exception.code.WishlistErrorCode

class WishlistAlreadyExistsException : BusinessException(WishlistErrorCode.WISHLIST_ALREADY_EXISTS)
