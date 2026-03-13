package com.hoppingmall.mall.wishlist.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.wishlist.exception.code.WishlistErrorCode

class WishlistAlreadyExistsException : BusinessException(WishlistErrorCode.WISHLIST_ALREADY_EXISTS)
